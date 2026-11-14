package io.github.gciatto.ktconstraints.core

/** Classical Boolean implication from left to right. */
data class BoolImplies(
    val left: BoolExpr,
    val right: BoolExpr,
) : BoolExpr {
    override val children: List<Expr> get() = listOf(left, right)
}
