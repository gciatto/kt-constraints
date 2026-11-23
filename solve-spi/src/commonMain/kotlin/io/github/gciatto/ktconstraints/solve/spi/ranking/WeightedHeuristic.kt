package io.github.gciatto.ktconstraints.solve.spi.ranking

/** A [heuristic] and the [weight] its scores are multiplied by. */
data class WeightedHeuristic(
    /** The heuristic. */
    val heuristic: RankingHeuristic,
    /** Multiplier of its scores. */
    val weight: Double = 1.0,
)
