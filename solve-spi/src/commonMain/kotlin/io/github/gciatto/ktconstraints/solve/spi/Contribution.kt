package io.github.gciatto.ktconstraints.solve.spi

/** What one heuristic added to a candidate's rank. */
data class Contribution(
    /** Heuristic name. */
    val heuristic: String,
    /** Its weight in the policy. */
    val weight: Double,
    /** Its unweighted score. */
    val score: Score,
)
