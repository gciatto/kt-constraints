package io.github.gciatto.ktconstraints.solve.kotlin.runtime

import io.github.gciatto.ktconstraints.core.Rational
import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.solve.SolveOptions
import io.github.gciatto.ktconstraints.solve.SolverDescriptor
import io.github.gciatto.ktconstraints.solve.kotlin.lp.Simplex
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
 * Optimizes a linear program's real objective with the exact simplex: an exact optimum, an unattained supremum or
 * infimum reported as a bound with a feasible incumbent, unboundedness or infeasibility. Emits at most one incumbent.
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
            finish(SolveStatus.UNKNOWN, if (released) TerminationReason.CANCELLED else TerminationReason.TIMEOUT)
        }
    }

    override fun release() {
        released = true
    }

    private fun solve(): SolutionEvent {
        val simplex = Simplex(program) { released || expired() }
        if (expired()) throw SimplexStopped()
        if (!simplex.check()) return finish(SolveStatus.INFEASIBLE, TerminationReason.COMPLETED)
        val goal = requireNotNull(program.objective)
        val sign = if (goal.maximize) Rational.ONE else Rational.of(-1)
        val score = goal.terms.mapValues { it.value * sign }
        if (!simplex.maximize(score)) return finish(SolveStatus.UNBOUNDED, TerminationReason.COMPLETED)
        val epsilon = simplex.epsilon()
        val solution = LinearSolution(program, List(program.columns.size) { simplex.valueOf(it).at(epsilon) })
        if (!solution.isFeasible) throw BackendExecutionException("${descriptor.id} produced a solution violating the program")
        val optimum = simplex.objectiveValue(score)
        val bound = optimum.real * sign + goal.constant
        val found = FloatingPointPrograms.found(model, solution)
        incumbent = found
        // A remaining δ term means the optimum is approached through strict rows but never reached.
        val attained = optimum.delta == Rational.ZERO
        finish(if (attained) SolveStatus.OPTIMAL else SolveStatus.FEASIBLE, TerminationReason.COMPLETED, bound)
        return found
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
