package io.github.gciatto.ktconstraints.core

/** Classical Boolean disjunction. */
data class BoolOr(
    val left: BoolExpr,
    val right: BoolExpr,
) : BoolExpr {
    override val children: List<Expr> get() = listOf(left, right)
}
