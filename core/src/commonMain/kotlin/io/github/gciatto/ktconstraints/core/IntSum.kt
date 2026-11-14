package io.github.gciatto.ktconstraints.core

/** Exact integer addition. */
data class IntSum(
    val left: IntExpr,
    val right: IntExpr,
) : IntExpr {
    override val children: List<Expr> get() = listOf(left, right)
}
