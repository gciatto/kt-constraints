package io.github.gciatto.ktconstraints.solve.spi.ranking

/** One heuristic's opinion of a backend: roughly in -1..1, higher is better, with a one-line [reason]. */
data class Score(
    /** Preference, roughly in -1..1. */
    val value: Double,
    /** Why, in one line. */
    val reason: String,
)
