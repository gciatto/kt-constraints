package io.github.gciatto.ktconstraints.core

/** Symbolic Boolean equivalence. */
data class BoolEquality(
    val left: BoolExpr,
    val right: BoolExpr,
) : BoolExpr {
    override val children: List<Expr> get() = listOf(left, right)
}
