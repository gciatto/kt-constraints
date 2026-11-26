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
    ;

    companion object {
        /** HiGHS' status for a model with no rows or columns. */
        const val MODEL_EMPTY = 6

        private const val MODEL_OPTIMAL = 7
        private const val MODEL_INFEASIBLE = 8
        private const val MODEL_UNBOUNDED_OR_INFEASIBLE = 9
        private const val MODEL_UNBOUNDED = 10
        private const val MODEL_TIME_LIMIT = 13
        private const val MODEL_INTERRUPT = 17
        private const val MODEL_HIGHS_INTERRUPT = 19

        /** Maps the status codes shared by the C and Wasm APIs, retaining unknown codes as [OTHER]. */
        fun of(model: Int): HighsStatus =
            when (model) {
                MODEL_OPTIMAL, MODEL_EMPTY -> OPTIMAL
                MODEL_INFEASIBLE -> INFEASIBLE
                MODEL_UNBOUNDED_OR_INFEASIBLE -> UNBOUNDED_OR_INFEASIBLE
                MODEL_UNBOUNDED -> UNBOUNDED
                MODEL_TIME_LIMIT -> TIME_LIMIT
                MODEL_INTERRUPT, MODEL_HIGHS_INTERRUPT -> INTERRUPTED
                else -> OTHER
            }
    }
}
