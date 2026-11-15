package io.github.gciatto.ktconstraints.core

/** Exact real equality. */
data class RealEquality(
    /** Left operand. */
    val left: RealExpr,
    /** Right operand. */
    val right: RealExpr,
) : BoolExpr {
    override val children: List<Expr> get() = listOf(left, right)
}
