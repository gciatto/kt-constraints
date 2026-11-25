package io.github.gciatto.ktconstraints.solve.highs

/** The HiGHS model statuses this library distinguishes. */
internal enum class HighsStatus {
    OPTIMAL,
    INFEASIBLE,
    UNBOUNDED,
    UNBOUNDED_OR_INFEASIBLE,

    /** Stopped by the time limit; a feasible incumbent may exist. */
    TIME_LIMIT,

    /** Stopped by [HighsRun.interrupt]. */
    INTERRUPTED,

    /** Any other status, which is not evidence of anything. */
    OTHER,
}
