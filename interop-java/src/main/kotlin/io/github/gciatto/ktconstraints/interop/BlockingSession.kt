package io.github.gciatto.ktconstraints.interop

import io.github.gciatto.ktconstraints.core.bool.BoolExpr
import io.github.gciatto.ktconstraints.solve.SolverDescriptor
import io.github.gciatto.ktconstraints.solve.incremental.CheckResult
import io.github.gciatto.ktconstraints.solve.incremental.Checkpoint
import io.github.gciatto.ktconstraints.solve.incremental.IncrementalSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.future.future
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CompletableFuture

/**
 * Java view of one [IncrementalSession], opened by [BlockingSolver.openSession]. Checks are satisfiability queries,
 * either blocking or as futures that the owning solver cancels when closed. Single consumer: do not overlap calls.
 */
class BlockingSession internal constructor(
    private val session: IncrementalSession,
    private val scope: CoroutineScope,
) : AutoCloseable {
    /** Engine and execution route of the session. */
    val descriptor: SolverDescriptor get() = session.descriptor

    /** Asserts [constraint] in the current frame. */
    fun add(constraint: BoolExpr) = session.add(constraint)

    /** Opens a frame and returns the checkpoint restoring the assertions present now. */
    fun push(): Checkpoint = session.push()

    /** Restores [checkpoint], invalidating it and newer checkpoints. */
    fun popTo(checkpoint: Checkpoint) = session.popTo(checkpoint)

    /** Checks on the calling thread the active assertions plus [assumptions], which hold for this check only. */
    @JvmOverloads
    fun check(
        assumptions: List<BoolExpr> = emptyList(),
        timeoutMillis: Long? = null,
    ): CheckResult = runBlocking { session.check(assumptions, timeoutMillis) }

    /** Checks in the background; cancelling the returned future cancels the check. */
    @JvmOverloads
    fun checkAsync(
        assumptions: List<BoolExpr> = emptyList(),
        timeoutMillis: Long? = null,
    ): CompletableFuture<CheckResult> = scope.future { session.check(assumptions, timeoutMillis) }

    override fun close() = session.close()
}
