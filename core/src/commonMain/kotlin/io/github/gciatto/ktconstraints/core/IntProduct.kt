package io.github.gciatto.ktconstraints.core

/** Exact integer multiplication. */
data class IntProduct(
    val left: IntExpr,
    val right: IntExpr,
) : IntExpr {
    override val children: List<Expr> get() = listOf(left, right)
}
