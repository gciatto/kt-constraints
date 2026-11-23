package io.github.gciatto.ktconstraints.core.bool

import io.github.gciatto.ktconstraints.core.Expr

/** Classical Boolean implication from left to right. */
data class BoolImplies(
    /** Left operand. */
    val left: BoolExpr,
    /** Right operand. */
    val right: BoolExpr,
) : BoolExpr {
    override val children: List<Expr> get() = listOf(left, right)
}
