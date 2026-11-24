package io.github.gciatto.ktconstraints.solve.blocking

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
 * Blocking view of one [IncrementalSession], opened by [BlockingSolver.openSession]. Checks are satisfiability
 * queries, either blocking or as futures that the owning solver cancels when closed. Single consumer: do not overlap
 * calls.
 */
actual class BlockingSession internal constructor(
    private val session: IncrementalSession,
    private val scope: CoroutineScope,
) : AutoCloseable {
    actual val descriptor: SolverDescriptor get() = session.descriptor

    actual fun add(constraint: BoolExpr) = session.add(constraint)

    actual fun push(): Checkpoint = session.push()

    actual fun popTo(checkpoint: Checkpoint) = session.popTo(checkpoint)

    @JvmOverloads
    actual fun check(
        assumptions: List<BoolExpr>,
        timeoutMillis: Long?,
    ): CheckResult = runBlocking { session.check(assumptions, timeoutMillis) }

    /** Checks in the background; cancelling the returned future cancels the check. */
    @JvmOverloads
    fun checkAsync(
        assumptions: List<BoolExpr> = emptyList(),
        timeoutMillis: Long? = null,
    ): CompletableFuture<CheckResult> = scope.future { session.check(assumptions, timeoutMillis) }

    actual override fun close() = session.close()
}
