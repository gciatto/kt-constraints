package io.github.gciatto.ktconstraints.solve.incremental
import io.github.gciatto.ktconstraints.core.Variable
import io.github.gciatto.ktconstraints.core.bool.BoolExpr
import io.github.gciatto.ktconstraints.model.Objective
import io.github.gciatto.ktconstraints.solve.SolverDescriptor
import io.github.gciatto.ktconstraints.solve.result.SolveResult

/**
 * Single-consumer assertion store over a model's variables, with reversible frames and repeated checks.
 * Repeated checks are satisfiability queries, not domain propagation.
 */
interface IncrementalSession : AutoCloseable {
    /** Engine and route executing this session. */
    val descriptor: SolverDescriptor

    /**
     * Adds [variable], with its domain, to the current frame; popping that frame forgets both. Variables already in
     * the session and unsupported sorts are rejected. Declared variables join the projected output.
     */
    fun declare(variable: Variable)

    /** Asserts [constraint] in the current frame; foreign variables and unsupported nodes change nothing. */
    fun add(constraint: BoolExpr)

    /** Opens a frame and returns the checkpoint restoring the assertions present now. */
    fun push(): Checkpoint

    /** Restores [checkpoint], invalidating it and newer checkpoints; stale or foreign checkpoints are rejected. */
    fun popTo(checkpoint: Checkpoint)

    /** Checks the active assertions plus [assumptions], which hold for this check only. */
    suspend fun check(
        assumptions: List<BoolExpr> = emptyList(),
        timeoutMillis: Long? = null,
    ): CheckResult

    /**
     * Optimizes [objective] over the active assertions plus [assumptions]; both the objective and the assumptions hold
     * for this call only, so the session can be edited and optimized again. The result has the meaning of a batch
     * solve: `OPTIMAL` with the optimum, `FEASIBLE` with an incumbent and any proven bound when a limit stops the
     * search, `INFEASIBLE`, `UNBOUNDED` or `UNKNOWN`. Routes without
     * [io.github.gciatto.ktconstraints.solve.SolverCapability.INCREMENTAL_OPTIMIZATION] reject every call.
     */
    suspend fun optimize(
        objective: Objective,
        assumptions: List<BoolExpr> = emptyList(),
        timeoutMillis: Long? = null,
    ): SolveResult

    /** Releases engine resources; idempotent. */
    override fun close()
}
