package io.github.gciatto.ktconstraints.solve.result

/** Solver result status; status and termination reason remain independent. */
enum class SolveStatus {
    FEASIBLE,
    OPTIMAL,
    INFEASIBLE,
    UNBOUNDED,
    INFEASIBLE_OR_UNBOUNDED,
    UNKNOWN,
}
