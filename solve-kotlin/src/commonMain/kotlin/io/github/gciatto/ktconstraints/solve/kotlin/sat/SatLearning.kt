package io.github.gciatto.ktconstraints.solve.kotlin.sat

/** First-UIP clause and the decision level at which its first literal becomes unit. */
internal class SatLearning(
    val clause: IntArray,
    val backtrackLevel: Int,
)
