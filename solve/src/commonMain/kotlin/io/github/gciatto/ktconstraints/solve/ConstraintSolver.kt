package io.github.gciatto.ktconstraints.solve

import io.github.gciatto.ktconstraints.model.ConstraintModel

/** Common batch-solving contract. Backend implementations will be added incrementally. */
interface ConstraintSolver : AutoCloseable {
    /** Discoverable backend and execution-route description. */
    val descriptor: SolverDescriptor

    /** Solves a frozen model; may suspend while a native, subprocess or Wasm backend executes. */
    suspend fun solve(
        model: ConstraintModel,
        options: SolveOptions = SolveOptions.DEFAULT,
    ): SolveResult

    /** Opens a single-pass search owned by this solver; the caller must close it when stopping early. */
    fun solutions(
        model: ConstraintModel,
        options: SolveOptions = SolveOptions.DEFAULT,
    ): SolutionStream
}
