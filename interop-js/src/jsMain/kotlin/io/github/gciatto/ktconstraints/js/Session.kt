package io.github.gciatto.ktconstraints.js

import io.github.gciatto.ktconstraints.solve.IncrementalSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlin.js.Promise

/**
 * Incremental assertion store over a model's variables with reversible frames. Repeated checks are satisfiability
 * queries, not propagation. One check at a time.
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

    /** Cancels a pending check and releases the session; idempotent. */
    fun close() {
        scope.cancel()
        session.close()
    }
}
