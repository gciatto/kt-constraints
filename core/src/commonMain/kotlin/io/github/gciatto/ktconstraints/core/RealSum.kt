package io.github.gciatto.ktconstraints.core

/** Exact real addition. */
data class RealSum(
    /** Left operand. */
    val left: RealExpr,
    /** Right operand. */
    val right: RealExpr,
) : RealExpr {
    override val children: List<Expr> get() = listOf(left, right)
}
