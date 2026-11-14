package io.github.gciatto.ktconstraints.core

/** Symbolic integer equality; Kotlin equality remains structural. */
data class IntEquality(
    val left: IntExpr,
    val right: IntExpr,
) : BoolExpr {
    override val children: List<Expr> get() = listOf(left, right)
}
