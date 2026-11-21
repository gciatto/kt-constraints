package io.github.gciatto.ktconstraints.solve.kotlin.cp

/** Store-owned restoration point; restoring a point consumes it and every younger point. */
internal class DomainCheckpoint(
    val owner: Any,
    val trailSize: Int,
)
