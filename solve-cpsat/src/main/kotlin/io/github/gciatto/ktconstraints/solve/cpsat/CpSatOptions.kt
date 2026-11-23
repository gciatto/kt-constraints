package io.github.gciatto.ktconstraints.solve.cpsat

import io.github.gciatto.ktconstraints.solve.BackendOptions
import io.github.gciatto.ktconstraints.solve.assignment.Assignment

/**
 * Per-solve CP-SAT tuning, overriding the [CpSatBackend] defaults: `SolveOptions() + CpSatOptions(workers = 8)`.
 * Satisfaction enumeration always uses one worker.
 */
data class CpSatOptions
    @JvmOverloads
    constructor(
        /** Search workers for optimization, 0 for CP-SAT's default, or null to keep the backend's. */
        val workers: Int? = null,
        /** Fixed random seed, or null to keep the backend's. */
        val randomSeed: Int? = null,
        /** Values the search tries first, e.g. a previous solution; they need not be consistent. */
        val hints: Assignment = Assignment.empty(),
    ) : BackendOptions {
        init {
            require(workers == null || workers >= 0) { "Worker count must be non-negative: $workers" }
        }
    }
