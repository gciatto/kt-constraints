package io.github.gciatto.ktconstraints.core

/** Non-strict exact real ordering. */
data class RealLessOrEqual(
    /** Left operand. */
    val left: RealExpr,
    /** Right operand. */
    val right: RealExpr,
) : BoolExpr {
    override val children: List<Expr> get() = listOf(left, right)
}
