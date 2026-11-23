package io.github.gciatto.ktconstraints.core.integer.relation

import io.github.gciatto.ktconstraints.core.Expr
import io.github.gciatto.ktconstraints.core.bool.BoolExpr
import io.github.gciatto.ktconstraints.core.integer.IntExpr

/** Symbolic integer equality; Kotlin equality remains structural. */
data class IntEquality(
    /** Left operand. */
    val left: IntExpr,
    /** Right operand. */
    val right: IntExpr,
) : BoolExpr {
    override val children: List<Expr> get() = listOf(left, right)
}
