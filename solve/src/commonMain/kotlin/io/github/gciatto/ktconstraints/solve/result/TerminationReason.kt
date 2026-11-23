package io.github.gciatto.ktconstraints.solve.result

/** Why execution stopped, independently of the mathematical search outcome. */
enum class TerminationReason {
    /** The requested search completed with its reported evidence. */
    COMPLETED,

    /** The requested time budget expired. */
    TIMEOUT,

    /** The caller cancelled execution. */
    CANCELLED,

    /** Execution was interrupted by its environment. */
    INTERRUPTED,

    /** A solution, memory, node or other resource limit stopped execution. */
    RESOURCE_LIMIT,

    /** The engine or transport failed; this is not evidence of infeasibility. */
    SOLVER_FAILURE,
}
