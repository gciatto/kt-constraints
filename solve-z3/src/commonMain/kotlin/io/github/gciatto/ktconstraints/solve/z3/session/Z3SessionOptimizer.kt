package io.github.gciatto.ktconstraints.solve.z3.session

import io.github.gciatto.ktconstraints.core.Rational
import io.github.gciatto.ktconstraints.core.Variable
import io.github.gciatto.ktconstraints.core.bool.BoolExpr
import io.github.gciatto.ktconstraints.core.evaluation.GroundEvaluator
import io.github.gciatto.ktconstraints.core.integer.IntExpr
import io.github.gciatto.ktconstraints.core.real.RealExpr
import io.github.gciatto.ktconstraints.model.Objective
import io.github.gciatto.ktconstraints.model.isReal
import io.github.gciatto.ktconstraints.solve.SolverDescriptor
import io.github.gciatto.ktconstraints.solve.assignment.Assignment
import io.github.gciatto.ktconstraints.solve.assignment.project
import io.github.gciatto.ktconstraints.solve.incremental.CheckStatus
import io.github.gciatto.ktconstraints.solve.result.SolveMetadata
import io.github.gciatto.ktconstraints.solve.result.SolveResult
import io.github.gciatto.ktconstraints.solve.result.SolveStatus
import io.github.gciatto.ktconstraints.solve.result.TerminationReason
import io.github.gciatto.ktconstraints.solve.spi.exception.BackendExecutionException
import io.github.gciatto.ktconstraints.solve.z3.port.Z3Port
import io.github.gciatto.ktconstraints.solve.z3.translation.Z3Translation
import kotlin.concurrent.Volatile
import kotlin.time.TimeSource

/**
 * One optimization over a [Z3Session]'s own context. Z3's optimizer runs over the session's translated assertions plus
 * the call's assumptions, and each of its claims is confirmed by a plain check of the session's solver in a temporary
 * frame, as batch solves do: an attained optimum `v` stands once nothing better than `v` exists, an unattained bound
 * `u` once nothing reaches `u`, and a refuting model becomes the incumbent for another round.
 */
internal class Z3SessionOptimizer<T>(
    private val translation: Z3Translation<T>,
    private val objective: Objective,
    /** Every term the session's solver holds: domains, constraints and their definedness. */
    private val asserted: List<T>,
    /** The active assertions, which every answer must satisfy. */
    private val active: List<BoolExpr>,
    private val assumptions: List<BoolExpr>,
    private val outputs: List<Variable>,
    private val descriptor: SolverDescriptor,
    private val timeoutMillis: Long?,
) {
    private val started = TimeSource.Monotonic.markNow()
    private val messages = mutableListOf<String>()

    /** The incumbent's objective value and projected assignment, read by a watchdog that abandoned the call. */
    @Volatile
    private var incumbent: Pair<Rational, Assignment>? = null
    private val value: Rational? get() = incumbent?.first

    /** The session's port, available only inside the confined call running [run]. */
    private lateinit var port: Z3Port<T>

    /** Optimizes on [confined], the session's port as its owner hands it to a confined call. */
    suspend fun run(confined: Z3Port<T>): SolveResult {
        port = confined
        val goal = translation.translate(objective.expression)
        val temporary = assumptions.map { translation.translate(it) } + translation.drainDefinedness()
        val constraints = asserted + temporary
        repeat(MAX_ROUNDS) {
            val remaining = remaining()
            if (remaining != null && remaining <= 0) return result(null, TerminationReason.TIMEOUT)
            val above = value?.let { listOf(improves(goal, it, strict = true)) }.orEmpty()
            val optimum = port.optimize(constraints + above, goal, objective.maximize, remaining)
            when (optimum.outcome.status) {
                CheckStatus.UNKNOWN -> return result(null, stopped(optimum.outcome.reason))
                CheckStatus.UNSATISFIABLE -> {
                    val incumbent = value ?: return result(null, TerminationReason.COMPLETED)
                    // Nothing improves on the incumbent, if the plain solver agrees with the optimizer.
                    if (!refuted(
                            temporary + improves(goal, incumbent, strict = true),
                        )
                    ) {
                        return result(incumbent, TerminationReason.COMPLETED)
                    }
                }
                CheckStatus.SATISFIABLE -> {
                    // shortcut: unboundedness is the optimizer's claim, as in batch solves.
                    if (optimum.infinity != Rational.ZERO) {
                        return SolveResult(SolveStatus.UNBOUNDED, null, descriptor, metadata = SolveMetadata(messages))
                    }
                    record()
                    val attained = optimum.epsilon == Rational.ZERO
                    if (!refuted(temporary + improves(goal, optimum.value, strict = attained))) {
                        return result(optimum.value, TerminationReason.COMPLETED)
                    }
                }
            }
        }
        messages += "Z3's optimizer claims could not be confirmed in $MAX_ROUNDS rounds"
        return result(null, TerminationReason.COMPLETED)
    }

    /** Plain check of the session plus [constraints] in a temporary frame: true and a better incumbent when satisfiable. */
    private suspend fun refuted(constraints: List<T>): Boolean {
        val remaining = remaining()?.coerceAtLeast(1)
        port.push()
        try {
            port.assert(constraints)
            val outcome = port.check(emptyList(), remaining)
            return when (outcome.status) {
                CheckStatus.UNSATISFIABLE -> false
                CheckStatus.SATISFIABLE -> true.also { record() }
                CheckStatus.UNKNOWN -> throw Unconfirmed(outcome.reason)
            }
        } finally {
            port.pop(1)
        }
    }

    /** Reads, validates and keeps the last satisfiable model when it improves on the incumbent. */
    private fun record() {
        val full = translation.assignment()
        if (!(active + assumptions).all { GroundEvaluator.evaluate(it, full) }) {
            throw BackendExecutionException("${descriptor.id} returned an assignment violating the session")
        }
        val found =
            when (val expression = objective.expression) {
                is RealExpr -> GroundEvaluator.evaluate(expression, full)
                is IntExpr -> Rational.of(GroundEvaluator.evaluate(expression, full))
                else -> error("Objectives are integer or real")
            }
        val current = value
        if (current == null || (if (objective.maximize) found > current else found < current)) incumbent = found to full.project(outputs)
    }

    /** `objective > bound` (or `≥` when not [strict]) when maximizing, its mirror when minimizing. */
    private fun improves(
        goal: T,
        bound: Rational,
        strict: Boolean,
    ): T {
        val literal = if (objective.isReal) port.real(bound) else port.int(bound.numerator)
        return when {
            objective.maximize && strict -> port.less(literal, goal)
            objective.maximize -> port.lessOrEqual(literal, goal)
            strict -> port.less(goal, literal)
            else -> port.lessOrEqual(goal, literal)
        }
    }

    private fun remaining(): Long? = timeoutMillis?.let { it - started.elapsedNow().inWholeMilliseconds }

    private fun stopped(reason: String): TerminationReason {
        messages += "Z3 unknown: $reason"
        val expired = timeoutMillis?.let { started.elapsedNow().inWholeMilliseconds >= it } == true
        return if (expired || reason == "timeout") TerminationReason.TIMEOUT else TerminationReason.COMPLETED
    }

    /**
     * The incumbent, `OPTIMAL` when the confirmed [bound] equals its value and `FEASIBLE` otherwise; without one,
     * `INFEASIBLE` after a completed proof and `UNKNOWN` after a stop.
     */
    fun result(
        bound: Rational?,
        reason: TerminationReason,
    ): SolveResult {
        val kept = incumbent
        val found = kept?.first
        val best = kept?.second
        val status =
            when {
                found == null ->
                    if (reason == TerminationReason.COMPLETED &&
                        bound == null &&
                        messages.isEmpty()
                    ) {
                        SolveStatus.INFEASIBLE
                    } else {
                        SolveStatus.UNKNOWN
                    }
                reason == TerminationReason.COMPLETED && bound == found -> SolveStatus.OPTIMAL
                else -> SolveStatus.FEASIBLE
            }
        val proven = bound?.takeIf { found != null }
        return if (objective.isReal) {
            SolveResult(
                status,
                best,
                descriptor,
                reason,
                metadata = SolveMetadata(messages),
                realObjectiveValue = found,
                realBestBound = proven,
            )
        } else {
            SolveResult(status, best, descriptor, reason, found?.numerator, proven?.numerator, SolveMetadata(messages))
        }
    }

    /** The answer when the plain solver could not settle a claim: the incumbent, if any, without a proof. */
    fun unconfirmed(reason: String): SolveResult = result(null, stopped(reason))

    /** A claim the plain solver could not settle within the budget. */
    class Unconfirmed(
        val reason: String,
    ) : RuntimeException("Unconfirmed optimizer claim: $reason")

    private companion object {
        const val MAX_ROUNDS = 8
    }
}
