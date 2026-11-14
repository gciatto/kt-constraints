package io.github.gciatto.ktconstraints.core

/** Classical Boolean conjunction. */
data class BoolAnd(
    val left: BoolExpr,
    val right: BoolExpr,
) : BoolExpr {
    override val children: List<Expr> get() = listOf(left, right)
}
