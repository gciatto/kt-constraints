package io.github.gciatto.ktconstraints.solve

/** Solver result status; status and termination reason remain independent. */
enum class SolveStatus {
    FEASIBLE,
    OPTIMAL,
    INFEASIBLE,
    UNBOUNDED,
    INFEASIBLE_OR_UNBOUNDED,
    UNKNOWN,
}

/** Result of a one-shot CP/SMT solve. */
data class SolveResult(
    val status: SolveStatus,
    val assignment: Assignment?,
    val descriptor: SolverDescriptor,
)
