package io.github.gciatto.ktconstraints.js.incremental
import io.github.gciatto.ktconstraints.js.expression.BoolExpression
import io.github.gciatto.ktconstraints.js.expression.IntExpression
import io.github.gciatto.ktconstraints.js.expression.RealExpression
import io.github.gciatto.ktconstraints.js.platform.AbortSignal
import io.github.gciatto.ktconstraints.js.platform.cancellable
import io.github.gciatto.ktconstraints.js.solve.Outcome
import io.github.gciatto.ktconstraints.model.Objective
import io.github.gciatto.ktconstraints.solve.incremental.IncrementalSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlin.js.Promise

/**
 * Incremental assertion store over a model's variables with reversible frames. Repeated checks are satisfiability
 * queries, not propagation; optimizations re-optimize the active assertions. One check or optimization at a time.
 */
@JsExport
class Session internal constructor(
    private val session: IncrementalSession,
) {
    private val scope = CoroutineScope(SupervisorJob())

    /** Asserts [constraint] in the current frame. */
    fun add(constraint: BoolExpression) = session.add(constraint.expr)

    /** Opens a frame; the checkpoint restores the assertions present now. */
    fun push(): Checkpoint = Checkpoint(session.push())

    /** Restores [checkpoint]; stale or foreign checkpoints are rejected. */
    fun popTo(checkpoint: Checkpoint) = session.popTo(checkpoint.checkpoint)

    /** Checks the active assertions plus [assumptions], which hold for this check only; [signal] cancels it. */
    fun check(
        assumptions: Array<BoolExpression> = emptyArray(),
        timeoutMillis: Int? = null,
        signal: AbortSignal? = null,
    ): Promise<CheckOutcome> =
        scope.cancellable(signal) {
            CheckOutcome(session.check(assumptions.map { it.expr }, timeoutMillis?.toLong()), assumptions)
        }

    /** Minimizes [objective] over the active assertions plus [assumptions], both for this call only; [signal] cancels it. */
    fun minimize(
        objective: IntExpression,
        assumptions: Array<BoolExpression> = emptyArray(),
        timeoutMillis: Int? = null,
        signal: AbortSignal? = null,
    ): Promise<Outcome> = optimize(Objective.Minimize(objective.expr), assumptions, timeoutMillis, signal)

    /** Maximizes [objective] over the active assertions plus [assumptions], both for this call only; [signal] cancels it. */
    fun maximize(
        objective: IntExpression,
        assumptions: Array<BoolExpression> = emptyArray(),
        timeoutMillis: Int? = null,
        signal: AbortSignal? = null,
    ): Promise<Outcome> = optimize(Objective.Maximize(objective.expr), assumptions, timeoutMillis, signal)

    /** Minimizes the real [objective] exactly; see [minimize]. */
    fun minimizeReal(
        objective: RealExpression,
        assumptions: Array<BoolExpression> = emptyArray(),
        timeoutMillis: Int? = null,
        signal: AbortSignal? = null,
    ): Promise<Outcome> = optimize(Objective.MinimizeReal(objective.expr), assumptions, timeoutMillis, signal)

    /** Maximizes the real [objective] exactly; see [maximize]. */
    fun maximizeReal(
        objective: RealExpression,
        assumptions: Array<BoolExpression> = emptyArray(),
        timeoutMillis: Int? = null,
        signal: AbortSignal? = null,
    ): Promise<Outcome> = optimize(Objective.MaximizeReal(objective.expr), assumptions, timeoutMillis, signal)

    private fun optimize(
        objective: Objective,
        assumptions: Array<BoolExpression>,
        timeoutMillis: Int?,
        signal: AbortSignal?,
    ): Promise<Outcome> =
        scope.cancellable(signal) { Outcome(session.optimize(objective, assumptions.map { it.expr }, timeoutMillis?.toLong())) }

    /** Cancels a pending check and releases the session; idempotent. */
    fun close() {
        scope.cancel()
        session.close()
    }
}
