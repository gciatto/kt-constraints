package io.github.gciatto.ktconstraints.solve.kotlin.sat

/** First-UIP clause, the decision level at which its first literal becomes unit, and its quality. */
internal class SatLearning(
    val clause: IntArray,
    val backtrackLevel: Int,
    /** Literal block distance: distinct decision levels among the clause's literals. */
    val blockDistance: Int,
)
