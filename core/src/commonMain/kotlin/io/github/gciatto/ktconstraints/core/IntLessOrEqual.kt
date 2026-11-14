package io.github.gciatto.ktconstraints.core

/** Non-strict mathematical integer ordering. */
data class IntLessOrEqual(
    val left: IntExpr,
    val right: IntExpr,
) : BoolExpr {
    override val children: List<Expr> get() = listOf(left, right)
}
