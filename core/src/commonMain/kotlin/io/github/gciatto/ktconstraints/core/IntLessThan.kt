package io.github.gciatto.ktconstraints.core

/** Strict mathematical integer ordering. */
data class IntLessThan(
    val left: IntExpr,
    val right: IntExpr,
) : BoolExpr {
    override val children: List<Expr> get() = listOf(left, right)
}
