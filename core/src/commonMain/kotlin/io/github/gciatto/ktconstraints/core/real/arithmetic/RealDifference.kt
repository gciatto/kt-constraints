package io.github.gciatto.ktconstraints.core.real.arithmetic

import io.github.gciatto.ktconstraints.core.Expr
import io.github.gciatto.ktconstraints.core.real.RealExpr

/** Exact real subtraction. */
data class RealDifference(
    /** Left operand. */
    val left: RealExpr,
    /** Right operand. */
    val right: RealExpr,
) : RealExpr {
    override val children: List<Expr> get() = listOf(left, right)
}
