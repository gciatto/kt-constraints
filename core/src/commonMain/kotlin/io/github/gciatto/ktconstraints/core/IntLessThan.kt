package io.github.gciatto.ktconstraints.core

/** Strict mathematical integer ordering. */
data class IntLessThan(
    /** Left operand. */
    val left: IntExpr,
    /** Right operand. */
    val right: IntExpr,
) : BoolExpr {
    override val children: List<Expr> get() = listOf(left, right)
}
