package io.github.gciatto.ktconstraints.core.integer.relation

import io.github.gciatto.ktconstraints.core.Expr
import io.github.gciatto.ktconstraints.core.bool.BoolExpr
import io.github.gciatto.ktconstraints.core.integer.IntExpr

/** Non-strict mathematical integer ordering. */
data class IntLessOrEqual(
    /** Left operand. */
    val left: IntExpr,
    /** Right operand. */
    val right: IntExpr,
) : BoolExpr {
    override val children: List<Expr> get() = listOf(left, right)
}
