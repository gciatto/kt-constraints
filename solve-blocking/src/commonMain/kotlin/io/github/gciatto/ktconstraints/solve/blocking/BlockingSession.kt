package io.github.gciatto.ktconstraints.solve.blocking

import io.github.gciatto.ktconstraints.core.bool.BoolExpr
import io.github.gciatto.ktconstraints.model.Objective
import io.github.gciatto.ktconstraints.solve.SolverDescriptor
import io.github.gciatto.ktconstraints.solve.incremental.CheckResult
import io.github.gciatto.ktconstraints.solve.incremental.Checkpoint
import io.github.gciatto.ktconstraints.solve.incremental.IncrementalSession
import io.github.gciatto.ktconstraints.solve.result.SolveResult
import kotlin.jvm.JvmOverloads

/**
 * Blocking view of one [IncrementalSession], opened by [BlockingSolver.openSession]. Checks are satisfiability
 * queries and optimizations re-optimize the active assertions; on the JVM they are also available as futures that the owning solver cancels when closed.
 * Single consumer: do not overlap calls.
 */
expect class BlockingSession : AutoCloseable {
    /** Engine and execution route of the session. */
    val descriptor: SolverDescriptor

    /** Asserts [constraint] in the current frame. */
    fun add(constraint: BoolExpr)

    /** Opens a frame and returns the checkpoint restoring the assertions present now. */
    fun push(): Checkpoint

    /** Restores [checkpoint], invalidating it and newer checkpoints. */
    fun popTo(checkpoint: Checkpoint)

    /** Checks on the calling thread the active assertions plus [assumptions], which hold for this check only. */
    @JvmOverloads
    fun check(
        assumptions: List<BoolExpr> = emptyList(),
        timeoutMillis: Long? = null,
    ): CheckResult

    /** Optimizes on the calling thread [objective] over the active assertions plus [assumptions], for this call only. */
    @JvmOverloads
    fun optimize(
        objective: Objective,
        assumptions: List<BoolExpr> = emptyList(),
        timeoutMillis: Long? = null,
    ): SolveResult

    override fun close()
}
