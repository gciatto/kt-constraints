package io.github.gciatto.ktconstraints.test.solve.oracle
import io.github.gciatto.ktconstraints.core.bool.BoolVar
import io.github.gciatto.ktconstraints.core.evaluation.GroundEvaluator
import io.github.gciatto.ktconstraints.core.evaluation.UndefinedExpressionException
import io.github.gciatto.ktconstraints.core.integer.IntVar
import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.model.Objective
import io.github.gciatto.ktconstraints.solve.SolveOptions
import io.github.gciatto.ktconstraints.solve.SolverDescriptor
import io.github.gciatto.ktconstraints.solve.assignment.Assignment
import io.github.gciatto.ktconstraints.solve.assignment.project
import io.github.gciatto.ktconstraints.solve.result.SolveResult
import io.github.gciatto.ktconstraints.solve.result.SolveStatus
import io.github.gciatto.ktconstraints.solve.result.TerminationReason
import io.github.gciatto.ktconstraints.solve.spi.managed.ManagedSolutionStream
import io.github.gciatto.ktconstraints.solve.stream.SolutionEvent
import io.github.gciatto.ktconstraints.solve.stream.SolutionFound
import io.github.gciatto.ktconstraints.solve.stream.SolveCompleted
import kotlinx.coroutines.yield
import org.gciatto.kt.math.BigInteger
import kotlin.time.TimeSource

internal class OracleStream(
    private val model: ConstraintModel,
    private val options: SolveOptions,
    private val candidates: OracleCandidates,
    descriptor: SolverDescriptor,
    onClosed: () -> Unit,
) : ManagedSolutionStream(descriptor, onClosed) {
    private val started = TimeSource.Monotonic.markNow()
    private val seen = hashSetOf<List<Any>>()
    private var incumbent: SolutionFound? = null
    private var emitted = 0

    override suspend fun readNext(): SolutionEvent {
        while (true) {
            yield()
            if (options.timeoutMillis?.let { started.elapsedNow().inWholeMilliseconds >= it } == true) {
                return complete(TerminationReason.TIMEOUT)
            }
            if (candidates.exhausted) return complete(TerminationReason.COMPLETED)
            if (options.solutionLimit?.let { emitted >= it } == true) return complete(TerminationReason.RESOURCE_LIMIT)
            val assignment = candidates.next()
            val event = candidate(assignment) ?: continue
            emitted++
            return event
        }
    }

    private fun candidate(assignment: Assignment): SolutionFound? {
        val value: BigInteger?
        try {
            if (!model.constraints.all { GroundEvaluator.evaluate(it, assignment) }) return null
            value =
                when (val objective = model.objective) {
                    is Objective.Minimize -> GroundEvaluator.evaluate(objective.expression, assignment)
                    is Objective.Maximize -> GroundEvaluator.evaluate(objective.expression, assignment)
                    null -> null
                }
        } catch (_: UndefinedExpressionException) {
            return null
        }
        val projected = assignment.project(model.outputVariables)
        val event = SolutionFound(projected, value)
        if (model.objective == null) {
            if (incumbent == null) incumbent = event
            val key: List<Any> =
                model.outputVariables.map { variable ->
                    when (variable) {
                        is BoolVar -> requireNotNull(projected.valueOf(variable))
                        is IntVar -> requireNotNull(projected.valueOf(variable))
                        else -> error("Unsupported projection")
                    }
                }
            return if (seen.add(key)) event else null
        }
        val previous = incumbent?.objectiveValue
        if (previous != null &&
            when (model.objective) {
                is Objective.Minimize -> requireNotNull(value) >= previous
                else -> requireNotNull(value) <= previous
            }
        ) {
            return null
        }
        incumbent = event
        return event
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

    override fun release() = Unit
}
