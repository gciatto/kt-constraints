package io.github.gciatto.ktconstraints.solve.highs

/** What one HiGHS run reported: its status and, when it holds a feasible solution, one value per column. */
internal class HighsOutcome(
    val status: HighsStatus,
    val values: List<Double>?,
    val detail: String = "",
)
