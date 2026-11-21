package io.github.gciatto.ktconstraints.solve.mip

import io.github.gciatto.ktconstraints.solve.BackendOptions

/**
 * Per-solve tuning of the OR-Tools MIP routes: `SolveOptions() + MipOptions(threads = 4)`. A positive [relativeGap]
 * lets the solver stop early, so results are then reported as feasible, never optimal.
 */
data class MipOptions(
    /** Solver threads, or null for the solver's default. */
    val threads: Int? = null,
    /** Accepted relative optimality gap, or null for an exact proof. */
    val relativeGap: Double? = null,
) : BackendOptions {
    init {
        require(threads == null || threads > 0) { "Thread count must be positive: $threads" }
        require(relativeGap == null || relativeGap >= 0.0) { "Relative gap must be non-negative: $relativeGap" }
    }
}
