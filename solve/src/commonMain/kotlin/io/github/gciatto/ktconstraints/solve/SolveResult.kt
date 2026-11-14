package io.github.gciatto.ktconstraints.solve

/** Result of a one-shot CP/SMT solve. */
data class SolveResult(
    val status: SolveStatus,
    val assignment: Assignment?,
    val descriptor: SolverDescriptor,
)
