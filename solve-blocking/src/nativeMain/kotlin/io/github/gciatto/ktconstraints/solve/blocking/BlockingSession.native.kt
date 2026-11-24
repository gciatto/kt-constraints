package io.github.gciatto.ktconstraints.solve.blocking

import io.github.gciatto.ktconstraints.core.bool.BoolExpr
import io.github.gciatto.ktconstraints.solve.SolverDescriptor
import io.github.gciatto.ktconstraints.solve.incremental.CheckResult
import io.github.gciatto.ktconstraints.solve.incremental.Checkpoint
import io.github.gciatto.ktconstraints.solve.incremental.IncrementalSession
import kotlinx.coroutines.runBlocking

/**
 * Blocking view of one [IncrementalSession], opened by [BlockingSolver.openSession]. Checks are satisfiability
 * queries that block the calling thread. Single consumer: do not overlap calls.
 */
actual class BlockingSession internal constructor(
    private val session: IncrementalSession,
) : AutoCloseable {
    actual val descriptor: SolverDescriptor get() = session.descriptor

    actual fun add(constraint: BoolExpr) = session.add(constraint)

    actual fun push(): Checkpoint = session.push()

    actual fun popTo(checkpoint: Checkpoint) = session.popTo(checkpoint)

    actual fun check(
        assumptions: List<BoolExpr>,
        timeoutMillis: Long?,
    ): CheckResult = runBlocking { session.check(assumptions, timeoutMillis) }

    actual override fun close() = session.close()
}
