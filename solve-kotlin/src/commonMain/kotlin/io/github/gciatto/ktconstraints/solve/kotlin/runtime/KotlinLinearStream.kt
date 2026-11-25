package io.github.gciatto.ktconstraints.solve.kotlin.runtime

import io.github.gciatto.ktconstraints.core.Rational
import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.solve.SolveOptions
import io.github.gciatto.ktconstraints.solve.SolverDescriptor
import io.github.gciatto.ktconstraints.solve.kotlin.lp.BranchAndBound
import io.github.gciatto.ktconstraints.solve.kotlin.lp.SimplexStopped
import io.github.gciatto.ktconstraints.solve.linear.FloatingPointPrograms
import io.github.gciatto.ktconstraints.solve.linear.LinearProgram
import io.github.gciatto.ktconstraints.solve.linear.LinearSolution
import io.github.gciatto.ktconstraints.solve.result.SolveResult
import io.github.gciatto.ktconstraints.solve.result.SolveStatus
import io.github.gciatto.ktconstraints.solve.result.TerminationReason
import io.github.gciatto.ktconstraints.solve.spi.exception.BackendExecutionException
import io.github.gciatto.ktconstraints.solve.spi.managed.ManagedSolutionStream
import io.github.gciatto.ktconstraints.solve.stream.SolutionEvent
import io.github.gciatto.ktconstraints.solve.stream.SolutionFound
import io.github.gciatto.ktconstraints.solve.stream.SolveCompleted
import kotlin.concurrent.Volatile
import kotlin.time.TimeSource

/**
 * Optimizes a linear or mixed-integer program's real objective with exact branch and bound over the simplex: an exact
 * optimum, an unattained supremum or infimum reported as a bound with a feasible incumbent, unboundedness or
 * infeasibility. Emits at most one incumbent; a stop keeps the best integral solution found so far.
 */
internal class KotlinLinearStream(
    private val model: ConstraintModel,
    private val options: SolveOptions,
    descriptor: SolverDescriptor,
    onClosed: () -> Unit,
) : ManagedSolutionStream(descriptor, onClosed) {
    private val program = LinearProgram.of(model)
    private val started = TimeSource.Monotonic.markNow()
    private var completion: SolveCompleted? = null
    private var incumbent: SolutionFound? = null
    private val search = BranchAndBound(program) { released || expired() }

    @Volatile
    private var released = false

    override suspend fun readNext(): SolutionEvent {
        completion?.let { pending ->
            val limited = incumbent != null && options.solutionLimit?.let { it <= 1 } == true
            return if (limited) finish(SolveStatus.FEASIBLE, TerminationReason.RESOURCE_LIMIT) else pending
        }
        return try {
            solve()
        } catch (_: SimplexStopped) {
            val reason = if (released) TerminationReason.CANCELLED else TerminationReason.TIMEOUT
            val best = search.incumbent ?: return finish(SolveStatus.UNKNOWN, reason)
            emit(best.values)
            finish(SolveStatus.FEASIBLE, reason)
        }
    }

    override fun release() {
        released = true
    }

    private fun solve(): SolutionEvent {
        if (expired()) throw SimplexStopped()
        val goal = requireNotNull(program.objective)
        val sign = if (goal.maximize) Rational.ONE else Rational.of(-1)
        return when (val outcome = search.maximize(goal.terms.mapValues { it.value * sign })) {
            BranchAndBound.Outcome.Infeasible -> finish(SolveStatus.INFEASIBLE, TerminationReason.COMPLETED)
            BranchAndBound.Outcome.Unbounded -> finish(SolveStatus.UNBOUNDED, TerminationReason.COMPLETED)
            is BranchAndBound.Outcome.Optimal -> {
                val found = emit(outcome.incumbent.values)
                val optimum = outcome.incumbent.score
                // A remaining δ term means the optimum is approached through strict rows but never reached.
                val attained = optimum.delta == Rational.ZERO
                val bound = optimum.real * sign + goal.constant
                finish(if (attained) SolveStatus.OPTIMAL else SolveStatus.FEASIBLE, TerminationReason.COMPLETED, bound)
                found
            }
        }
    }

    /** Checks exact [values] against the program and records them as the incumbent. */
    private fun emit(values: List<Rational>): SolutionFound {
        val solution = LinearSolution(program, values)
        if (!solution.isFeasible) throw BackendExecutionException("${descriptor.id} produced a solution violating the program")
        return FloatingPointPrograms.found(model, solution).also { incumbent = it }
    }

    private fun expired(): Boolean = options.timeoutMillis?.let { started.elapsedNow().inWholeMilliseconds >= it } == true

    private fun finish(
        status: SolveStatus,
        reason: TerminationReason,
        bound: Rational? = completion?.result?.realBestBound,
    ): SolveCompleted {
        val found = incumbent
        return SolveCompleted(
            SolveResult(
                status,
                found?.assignment,
                descriptor,
                reason,
                realObjectiveValue = found?.realObjectiveValue,
                realBestBound = bound?.takeIf { status != SolveStatus.OPTIMAL || it == found?.realObjectiveValue },
            ),
        ).also { completion = it }
    }
}
