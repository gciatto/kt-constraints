package io.github.gciatto.ktconstraints.core.integer.arithmetic

import io.github.gciatto.ktconstraints.core.Expr
import io.github.gciatto.ktconstraints.core.integer.IntExpr

/** Exact integer subtraction. */
data class IntDifference(
    /** Left operand. */
    val left: IntExpr,
    /** Right operand. */
    val right: IntExpr,
) : IntExpr {
    override val children: List<Expr> get() = listOf(left, right)
}
