package io.github.gciatto.ktconstraints.solve

/** The single definitive terminal event of a solution stream. */
data class SolveCompleted(
    /** Search outcome and its independent termination reason. */
    val result: SolveResult,
) : SolutionEvent
