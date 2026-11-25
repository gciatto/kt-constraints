package io.github.gciatto.ktconstraints.solve.highs

import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.solve.SolveOptions
import io.github.gciatto.ktconstraints.solve.SolverDescriptor
import io.github.gciatto.ktconstraints.solve.linear.FloatingPointPrograms
import io.github.gciatto.ktconstraints.solve.linear.LinearProgram
import io.github.gciatto.ktconstraints.solve.result.SolveMetadata
import io.github.gciatto.ktconstraints.solve.result.SolveResult
import io.github.gciatto.ktconstraints.solve.result.SolveStatus
import io.github.gciatto.ktconstraints.solve.result.TerminationReason
import io.github.gciatto.ktconstraints.solve.spi.exception.BackendExecutionException
import io.github.gciatto.ktconstraints.solve.spi.managed.ManagedSolutionStream
import io.github.gciatto.ktconstraints.solve.stream.SolutionEvent
import io.github.gciatto.ktconstraints.solve.stream.SolutionFound
import io.github.gciatto.ktconstraints.solve.stream.SolveCompleted

/** One HiGHS run, emitting at most one exactly checked incumbent and then its completion. */
internal class HighsStream(
    private val model: ConstraintModel,
    private val program: LinearProgram,
    private val options: SolveOptions,
    descriptor: SolverDescriptor,
    private val run: HighsRun,
    onClosed: () -> Unit,
) : ManagedSolutionStream(descriptor, onClosed) {
    private var completion: SolveCompleted? = null
    private var incumbent: SolutionFound? = null
    private val messages = mutableListOf<String>()

    override suspend fun readNext(): SolutionEvent {
        completion?.let { pending ->
            // A solution limit stops the stream at its incumbent: optimality is not claimed beyond the limit.
            val limited = incumbent != null && options.solutionLimit?.let { it <= 1 } == true
            return if (limited) finish(SolveStatus.FEASIBLE, incumbent, TerminationReason.RESOURCE_LIMIT) else pending
        }
        if (options.timeoutMillis == 0L) return finish(SolveStatus.UNKNOWN, null, TerminationReason.TIMEOUT)
        val outcome = run.run(program, options.timeoutMillis)
        if (outcome.detail.isNotEmpty()) messages += outcome.detail
        return when (outcome.status) {
            HighsStatus.OPTIMAL -> incumbent(outcome, SolveStatus.OPTIMAL, TerminationReason.COMPLETED)
            HighsStatus.INFEASIBLE -> finish(SolveStatus.INFEASIBLE, null, TerminationReason.COMPLETED)
            HighsStatus.UNBOUNDED -> finish(SolveStatus.UNBOUNDED, null, TerminationReason.COMPLETED)
            HighsStatus.UNBOUNDED_OR_INFEASIBLE -> finish(SolveStatus.INFEASIBLE_OR_UNBOUNDED, null, TerminationReason.COMPLETED)
            HighsStatus.TIME_LIMIT -> incumbent(outcome, SolveStatus.FEASIBLE, TerminationReason.TIMEOUT)
            HighsStatus.INTERRUPTED -> incumbent(outcome, SolveStatus.FEASIBLE, TerminationReason.CANCELLED)
            HighsStatus.OTHER -> throw BackendExecutionException("${descriptor.id} stopped without evidence: ${outcome.detail}")
        }
    }

    override fun release() = run.interrupt()

    /** Emits the checked incumbent of [outcome], if any, with [status]; without one, completes as unknown. */
    private fun incumbent(
        outcome: HighsOutcome,
        status: SolveStatus,
        reason: TerminationReason,
    ): SolutionEvent {
        val values = outcome.values ?: return finish(SolveStatus.UNKNOWN, null, reason)
        val solution = FloatingPointPrograms.accept(program, values, options, descriptor.id)
        messages += "Floating-point solution: largest exact excess ${solution.violation}"
        val found = FloatingPointPrograms.found(model, solution)
        // HiGHS calls a solved satisfaction model optimal; without an objective it is only a feasible solution.
        finish(if (model.objective == null && status == SolveStatus.OPTIMAL) SolveStatus.FEASIBLE else status, found, reason)
        incumbent = found
        return found
    }

    private fun finish(
        status: SolveStatus,
        found: SolutionFound?,
        reason: TerminationReason,
    ): SolveCompleted {
        val optimal = status == SolveStatus.OPTIMAL
        return SolveCompleted(
            SolveResult(
                status,
                found?.assignment,
                descriptor,
                reason,
                found?.objectiveValue,
                found?.objectiveValue?.takeIf { optimal },
                SolveMetadata(messages.toList()),
                found?.realObjectiveValue,
                found?.realObjectiveValue?.takeIf { optimal },
            ),
        ).also { completion = it }
    }
}
