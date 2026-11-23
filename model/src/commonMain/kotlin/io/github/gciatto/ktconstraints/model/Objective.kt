package io.github.gciatto.ktconstraints.model

import io.github.gciatto.ktconstraints.core.integer.IntExpr

/** Optimization objective, separate from satisfiability constraints. */
sealed interface Objective {
    /** Minimize an integer expression. */
    data class Minimize(
        val expression: IntExpr,
    ) : Objective

    /** Maximize an integer expression. */
    data class Maximize(
        val expression: IntExpr,
    ) : Objective
}
