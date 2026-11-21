package io.github.gciatto.ktconstraints.solve.kotlin.arithmetic

/** Constraints needed to reconstruct one eliminated variable after the remaining variables are assigned. */
internal class EliminationFrame(
    val variable: Int,
    val rows: List<LinearInequality>,
)
