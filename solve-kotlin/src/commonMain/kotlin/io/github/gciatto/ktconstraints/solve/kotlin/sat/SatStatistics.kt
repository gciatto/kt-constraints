package io.github.gciatto.ktconstraints.solve.kotlin.sat

/** Cumulative work counters retained across incremental checks. */
internal class SatStatistics {
    var decisions: Long = 0
    var conflicts: Long = 0
    var propagations: Long = 0
}
