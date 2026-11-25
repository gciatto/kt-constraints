package io.github.gciatto.ktconstraints.solve.choco.runtime
import io.github.gciatto.ktconstraints.core.bool.BoolVar
import io.github.gciatto.ktconstraints.core.evaluation.GroundEvaluator
import io.github.gciatto.ktconstraints.core.integer.IntVar
import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.model.Objective
import io.github.gciatto.ktconstraints.solve.SolveOptions
import io.github.gciatto.ktconstraints.solve.SolverDescriptor
import io.github.gciatto.ktconstraints.solve.assignment.Assignment
import io.github.gciatto.ktconstraints.solve.assignment.project
import io.github.gciatto.ktconstraints.solve.backend
import io.github.gciatto.ktconstraints.solve.choco.ChocoOptions
import io.github.gciatto.ktconstraints.solve.choco.ChocoSearch
import io.github.gciatto.ktconstraints.solve.choco.translation.ChocoTranslation
import io.github.gciatto.ktconstraints.solve.result.SolveResult
import io.github.gciatto.ktconstraints.solve.result.SolveStatus
import io.github.gciatto.ktconstraints.solve.result.TerminationReason
import io.github.gciatto.ktconstraints.solve.spi.exception.BackendExecutionException
import io.github.gciatto.ktconstraints.solve.spi.managed.ManagedSolutionStream
import io.github.gciatto.ktconstraints.solve.spi.support.rejectRealObjective
import io.github.gciatto.ktconstraints.solve.stream.SolutionEvent
import io.github.gciatto.ktconstraints.solve.stream.SolutionFound
import io.github.gciatto.ktconstraints.solve.stream.SolveCompleted
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import org.chocosolver.util.criteria.Criterion
import org.gciatto.kt.math.BigInteger
import kotlin.time.TimeSource

/**
 * One Choco search over a model owned by this stream. Each read resumes the search for one more solution on the default
 * dispatcher; a stop criterion observes release, coroutine cancellation (thread interruption) and the deadline.
 */
internal class ChocoStream(
    private val model: ConstraintModel,
    private val options: SolveOptions,
    descriptor: SolverDescriptor,
    onClosed: () -> Unit,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ManagedSolutionStream(descriptor, onClosed) {
    private val translation = ChocoTranslation(model)
    private val started = TimeSource.Monotonic.markNow()
    private val solver = translation.chocoModel.solver

    @Volatile
    private var released = false
    private var incumbent: SolutionFound? = null
    private var emitted = 0
    private val seen = hashSetOf<List<Any?>>()

    init {
        solver.addStopCriterion(Criterion { released || Thread.currentThread().isInterrupted || expired() })
        val decisions = translation.natives.values.toTypedArray()
        options.backend<ChocoOptions>()?.search?.takeIf { decisions.isNotEmpty() }?.let {
            solver.setSearch(
                it.strategy(decisions),
                ChocoSearch.INPUT_ORDER_LOWER_BOUND.strategy(translation.chocoModel.retrieveIntVars(true)),
            )
        }
    }

    override suspend fun readNext(): SolutionEvent {
        var event: SolutionEvent? = null
        while (event == null) {
            event =
                when {
                    options.solutionLimit?.let { emitted >= it } == true -> complete(TerminationReason.RESOURCE_LIMIT)
                    !runInterruptible(dispatcher) { solver.solve() } -> finished()
                    else -> solution()
                }
        }
        if (event is SolutionFound) {
            incumbent = event
            emitted++
        }
        return event
    }

    override fun release() {
        released = true
    }

    private fun expired(): Boolean = options.timeoutMillis?.let { started.elapsedNow().inWholeMilliseconds >= it } == true

    private fun finished(): SolveCompleted =
        when {
            !solver.isStopCriterionMet -> complete(TerminationReason.COMPLETED)
            expired() -> complete(TerminationReason.TIMEOUT)
            // Released or interrupted: the stream already holds its terminal result, so this completion is discarded.
            else -> complete(TerminationReason.CANCELLED)
        }

    /** The validated projection of the current solution, or null when it repeats or does not improve. */
    private fun solution(): SolutionFound? {
        val full = translation.assignment()
        if (!model.constraints.all { GroundEvaluator.evaluate(it, full) }) {
            throw BackendExecutionException("${descriptor.id} returned an assignment violating the model")
        }
        val projected = full.project(model.outputVariables)
        return when (val objective = model.objective) {
            null -> {
                val key = model.outputVariables.map { if (it is BoolVar) projected.valueOf(it) else projected.valueOf(it as IntVar) }
                if (seen.add(key)) SolutionFound(projected) else null
            }
            is Objective.Minimize -> improving(GroundEvaluator.evaluate(objective.expression, full), projected, maximize = false)
            is Objective.Maximize -> improving(GroundEvaluator.evaluate(objective.expression, full), projected, maximize = true)
            is Objective.MinimizeReal, is Objective.MaximizeReal ->
                rejectRealObjective()
        }
    }

    private fun improving(
        value: BigInteger,
        projected: Assignment,
        maximize: Boolean,
    ): SolutionFound? {
        val previous = incumbent?.objectiveValue
        if (previous != null && (if (maximize) value <= previous else value >= previous)) return null
        return SolutionFound(projected, value)
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
}
