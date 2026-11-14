package io.github.gciatto.ktconstraints.core

/** Exact integer subtraction. */
data class IntDifference(
    val left: IntExpr,
    val right: IntExpr,
) : IntExpr {
    override val children: List<Expr> get() = listOf(left, right)
}
