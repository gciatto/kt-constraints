package io.github.gciatto.ktconstraints.core

/** Strict exact real ordering. */
data class RealLessThan(
    /** Left operand. */
    val left: RealExpr,
    /** Right operand. */
    val right: RealExpr,
) : BoolExpr {
    override val children: List<Expr> get() = listOf(left, right)
}
