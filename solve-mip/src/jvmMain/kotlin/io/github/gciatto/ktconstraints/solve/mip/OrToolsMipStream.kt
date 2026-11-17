package io.github.gciatto.ktconstraints.solve.mip

import com.google.ortools.linearsolver.MPSolver
import com.google.ortools.linearsolver.MPSolverParameters
import io.github.gciatto.ktconstraints.core.GroundEvaluator
import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.model.Objective
import io.github.gciatto.ktconstraints.solve.Assignment
import io.github.gciatto.ktconstraints.solve.SolutionEvent
import io.github.gciatto.ktconstraints.solve.SolutionFound
import io.github.gciatto.ktconstraints.solve.SolveCompleted
import io.github.gciatto.ktconstraints.solve.SolveOptions
import io.github.gciatto.ktconstraints.solve.SolveResult
import io.github.gciatto.ktconstraints.solve.SolveStatus
import io.github.gciatto.ktconstraints.solve.SolverDescriptor
import io.github.gciatto.ktconstraints.solve.TerminationReason
import io.github.gciatto.ktconstraints.solve.project
import io.github.gciatto.ktconstraints.solve.spi.BackendExecutionException
import io.github.gciatto.ktconstraints.solve.spi.BackendUnavailableException
import io.github.gciatto.ktconstraints.solve.spi.ManagedSolutionStream
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
    private val program = MipLowering.lower(model)
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
            populate(native)
            solver = native
            thread = Thread({ search(native) }, THREAD_NAME).apply { isDaemon = true }.also { it.start() }
        }
    }

    private fun populate(native: MPSolver) {
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
            val parameters = MPSolverParameters().apply { setDoubleParam(MPSolverParameters.DoubleParam.RELATIVE_MIP_GAP, 0.0) }
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
            val values = if (solved) native.variables().map { it.solutionValue().roundToLong() }.toLongArray() else null
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

    private fun interpret(result: OrToolsMipOutcome): SolutionEvent {
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

    private fun solution(values: LongArray): SolutionFound {
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
                null -> null
            }
        return SolutionFound(full.project(model.outputVariables), objective)
    }

    private fun complete(reason: TerminationReason): SolveCompleted {
        val best = incumbent
        val status =
            when {
                best == null -> if (reason == TerminationReason.COMPLETED) SolveStatus.INFEASIBLE else SolveStatus.UNKNOWN
                model.objective != null && reason == TerminationReason.COMPLETED -> SolveStatus.OPTIMAL
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
            ),
        )
    }

    companion object {
        /** Name of every MIP search thread. */
        const val THREAD_NAME = "kt-constraints-mip"
    }
}
