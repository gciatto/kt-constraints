package io.github.gciatto.ktconstraints.solve.mip.ortools
import com.google.ortools.linearsolver.MPSolver
import com.google.ortools.linearsolver.MPSolverParameters
import io.github.gciatto.ktconstraints.core.evaluation.GroundEvaluator
import io.github.gciatto.ktconstraints.core.integer.IntExpr
import io.github.gciatto.ktconstraints.core.real.RealExpr
import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.model.Objective
import io.github.gciatto.ktconstraints.model.isReal
import io.github.gciatto.ktconstraints.solve.SolveOptions
import io.github.gciatto.ktconstraints.solve.SolverDescriptor
import io.github.gciatto.ktconstraints.solve.assignment.Assignment
import io.github.gciatto.ktconstraints.solve.assignment.project
import io.github.gciatto.ktconstraints.solve.backend
import io.github.gciatto.ktconstraints.solve.linear.LinearProgram
import io.github.gciatto.ktconstraints.solve.linear.LinearSolution
import io.github.gciatto.ktconstraints.solve.linear.exactRational
import io.github.gciatto.ktconstraints.solve.mip.MipOptions
import io.github.gciatto.ktconstraints.solve.mip.program.MipLowering
import io.github.gciatto.ktconstraints.solve.mip.program.RealPrograms
import io.github.gciatto.ktconstraints.solve.result.SolveMetadata
import io.github.gciatto.ktconstraints.solve.result.SolveResult
import io.github.gciatto.ktconstraints.solve.result.SolveStatus
import io.github.gciatto.ktconstraints.solve.result.TerminationReason
import io.github.gciatto.ktconstraints.solve.spi.exception.BackendExecutionException
import io.github.gciatto.ktconstraints.solve.spi.exception.BackendUnavailableException
import io.github.gciatto.ktconstraints.solve.spi.managed.ManagedSolutionStream
import io.github.gciatto.ktconstraints.solve.spi.support.rejectRealObjective
import io.github.gciatto.ktconstraints.solve.stream.SolutionEvent
import io.github.gciatto.ktconstraints.solve.stream.SolutionFound
import io.github.gciatto.ktconstraints.solve.stream.SolveCompleted
import kotlinx.coroutines.future.await
import org.gciatto.kt.math.BigInteger
import java.util.concurrent.CompletableFuture
import kotlin.math.roundToLong
import kotlin.time.TimeSource

/**
 * One MIP solve on its own daemon thread, emitting at most one validated solution and then its completion. The thread
 * owns the native solver and deletes it; [release] interrupts it only under the lock that guards that deletion.
 */
internal class OrToolsMipStream(
    private val model: ConstraintModel,
    private val options: SolveOptions,
    descriptor: SolverDescriptor,
    private val engine: String,
    private val solveLimitMillis: Long?,
    onClosed: () -> Unit,
) : ManagedSolutionStream(descriptor, onClosed) {
    private val tuning = options.backend<MipOptions>()
    private val exact = (tuning?.relativeGap ?: 0.0) == 0.0

    /** The linear program of a model with reals, solved in floating point; null for the exact integer lowering. */
    private val linear = if (RealPrograms.isReal(model)) RealPrograms.lower(model, options) else null
    private val program = if (linear == null) MipLowering.lower(model) else null
    private val messages = mutableListOf<String>()
    private val started = TimeSource.Monotonic.markNow()
    private val outcome = CompletableFuture<OrToolsMipOutcome>()
    private val lock = Any()
    private var solver: MPSolver? = null
    private var thread: Thread? = null
    private var limitReason = TerminationReason.COMPLETED

    @Volatile
    private var released = false
    private var incumbent: SolutionFound? = null
    private var completion: SolveCompleted? = null

    override suspend fun readNext(): SolutionEvent {
        val remaining = options.timeoutMillis?.let { it - started.elapsedNow().inWholeMilliseconds }
        val pending = completion
        return when {
            pending != null && incumbent != null && options.solutionLimit?.let { it <= 1 } == true ->
                complete(
                    TerminationReason.RESOURCE_LIMIT,
                )
            pending != null -> pending
            remaining != null && remaining <= 0 -> complete(TerminationReason.TIMEOUT)
            else -> {
                start(remaining)
                interpret(outcome.await())
            }
        }
    }

    override fun release() {
        released = true
        val search =
            synchronized(lock) {
                if (solveLimitMillis == null) solver?.interruptSolve()
                thread
            }
        // HiGHS cannot be interrupted: its solve ends at the backend limit on its own daemon thread.
        if (solveLimitMillis == null) search?.join()
        outcome.complete(OrToolsMipOutcome(MPSolver.ResultStatus.NOT_SOLVED, null))
    }

    private fun start(remaining: Long?) {
        val limits =
            listOfNotNull(
                remaining?.let { it to TerminationReason.TIMEOUT },
                solveLimitMillis?.let { it to TerminationReason.RESOURCE_LIMIT },
            )
        val limit = limits.minByOrNull { it.first }
        limitReason = limit?.second ?: TerminationReason.COMPLETED
        synchronized(lock) {
            if (released) return
            val native = MPSolver.createSolver(engine) ?: throw BackendUnavailableException("OR-Tools was built without $engine")
            native.suppressOutput()
            limit?.let { native.setTimeLimit(it.first) }
            tuning?.threads?.let { native.setNumThreads(it) }
            populate(native)
            solver = native
            thread = Thread({ search(native) }, THREAD_NAME).apply { isDaemon = true }.also { it.start() }
        }
    }

    private fun populate(native: MPSolver) {
        linear?.let { return LinearOrTools.populate(native, it) }
        val program = requireNotNull(program)
        val variables = program.variables.mapIndexed { i, v -> native.makeIntVar(v.min.toDouble(), v.max.toDouble(), "v$i") }
        for (row in program.rows) {
            val constraint =
                native.makeConstraint(
                    row.min?.toDouble() ?: -MPSolver.infinity(),
                    row.max?.toDouble() ?: MPSolver.infinity(),
                )
            row.terms.forEach { (i, c) -> constraint.setCoefficient(variables[i], c.toDouble()) }
        }
        program.objective?.let { goal ->
            val objective = native.objective()
            goal.terms.forEach { (i, c) -> objective.setCoefficient(variables[i], c.toDouble()) }
            objective.setOffset(goal.constant.toDouble())
            if (goal.maximize) objective.setMaximization() else objective.setMinimization()
        }
    }

    @Suppress("TooGenericExceptionCaught") // Any native failure must reach the consumer instead of killing the thread.
    private fun search(native: MPSolver) {
        try {
            val parameters =
                MPSolverParameters().apply {
                    setDoubleParam(
                        MPSolverParameters.DoubleParam.RELATIVE_MIP_GAP,
                        tuning?.relativeGap ?: 0.0,
                    )
                }
            val status =
                try {
                    native.solve(parameters)
                } catch (unmapped: IllegalArgumentException) {
                    // shortcut: OR-Tools' HiGHS wrapper reports a stop at its time limit as status 99, which the Java enum
                    // cannot map; its incumbent is unreachable, so the stop yields no solution. Revisit on OR-Tools upgrades.
                    if (unmapped.message?.contains("ResultStatus") != true) throw unmapped
                    MPSolver.ResultStatus.NOT_SOLVED
                }
            val solved = status == MPSolver.ResultStatus.OPTIMAL || status == MPSolver.ResultStatus.FEASIBLE
            val values = if (solved) native.variables().map { it.solutionValue() }.toDoubleArray() else null
            outcome.complete(OrToolsMipOutcome(status, values))
        } catch (problem: Throwable) {
            outcome.completeExceptionally(problem)
        } finally {
            synchronized(lock) {
                solver = null
                native.delete()
            }
        }
    }

    private fun interpret(result: OrToolsMipOutcome): SolutionEvent =
        if (result.status == MPSolver.ResultStatus.UNBOUNDED) {
            // Only real models have unbounded columns; integer domains are always bounded.
            SolveCompleted(SolveResult(SolveStatus.UNBOUNDED, null, descriptor)).also { completion = it }
        } else {
            conclude(result)
        }

    private fun conclude(result: OrToolsMipOutcome): SolutionEvent {
        val proven = result.status == MPSolver.ResultStatus.OPTIMAL || result.status == MPSolver.ResultStatus.INFEASIBLE
        val reason =
            when {
                proven -> TerminationReason.COMPLETED
                released -> TerminationReason.CANCELLED
                limitReason != TerminationReason.COMPLETED -> limitReason
                else -> throw BackendExecutionException("${descriptor.id} stopped without proof: ${result.status}")
            }
        val values = result.values ?: return complete(reason)
        val found = solution(values)
        incumbent = found
        completion = complete(reason)
        return found
    }

    private fun solution(found: DoubleArray): SolutionFound {
        linear?.let { return linearSolution(it, found) }
        val program = requireNotNull(program)
        val values = found.map { it.roundToLong() }
        val full =
            try {
                Assignment.of(
                    booleans = program.booleans.mapValues { (_, i) -> values[i] == 1L },
                    integers = program.integers.mapValues { (_, i) -> BigInteger.of(values[i]) },
                )
            } catch (problem: IllegalArgumentException) {
                throw BackendExecutionException("${descriptor.id} returned values outside the declared domains", problem)
            }
        if (program.booleans.values.any { values[it] !in 0L..1L } || !model.constraints.all { GroundEvaluator.evaluate(it, full) }) {
            throw BackendExecutionException("${descriptor.id} returned an assignment violating the model")
        }
        val objective =
            when (val goal = model.objective) {
                is Objective.Minimize -> GroundEvaluator.evaluate(goal.expression, full)
                is Objective.Maximize -> GroundEvaluator.evaluate(goal.expression, full)
                is Objective.MinimizeReal, is Objective.MaximizeReal -> rejectRealObjective()
                null -> null
            }
        return SolutionFound(full.project(model.outputVariables), objective)
    }

    /** Reads floating-point [values] exactly, accepting them only within the caller's tolerances. */
    private fun linearSolution(
        program: LinearProgram,
        values: DoubleArray,
    ): SolutionFound {
        val tolerance = requireNotNull(options.numericTolerance)
        val solution =
            try {
                LinearSolution.ofDoubles(program, values.toList(), tolerance.integrality)
            } catch (problem: IllegalStateException) {
                throw BackendExecutionException("${descriptor.id}: ${problem.message}", problem)
            }
        if (solution.violation > exactRational(tolerance.feasibility)) {
            throw BackendExecutionException(
                "${descriptor.id} exceeded a constraint by ${solution.violation}, beyond ${tolerance.feasibility}",
            )
        }
        messages += "Floating-point solution: largest exact excess ${solution.violation} (tolerance ${tolerance.feasibility})"
        val full = solution.assignment()
        val projected = full.project(model.outputVariables)
        val goal = model.objective ?: return SolutionFound(projected)
        return if (goal.isReal) {
            SolutionFound(projected, realObjectiveValue = GroundEvaluator.evaluate(goal.expression as RealExpr, full))
        } else {
            SolutionFound(projected, GroundEvaluator.evaluate(goal.expression as IntExpr, full))
        }
    }

    private fun complete(reason: TerminationReason): SolveCompleted {
        val best = incumbent
        val status =
            when {
                best == null -> if (reason == TerminationReason.COMPLETED) SolveStatus.INFEASIBLE else SolveStatus.UNKNOWN
                // A positive gap tolerance stops the proof early: the incumbent is not proven optimal.
                model.objective != null && reason == TerminationReason.COMPLETED && exact -> SolveStatus.OPTIMAL
                else -> SolveStatus.FEASIBLE
            }
        return SolveCompleted(
            SolveResult(
                status,
                best?.assignment,
                descriptor,
                reason,
                best?.objectiveValue,
                if (status == SolveStatus.OPTIMAL) best?.objectiveValue else null,
                SolveMetadata(messages.toList()),
                best?.realObjectiveValue,
                if (status == SolveStatus.OPTIMAL) best?.realObjectiveValue else null,
            ),
        )
    }

    companion object {
        /** Name of every MIP search thread. */
        const val THREAD_NAME = "kt-constraints-mip"
    }
}
