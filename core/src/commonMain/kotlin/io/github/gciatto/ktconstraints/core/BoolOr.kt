package io.github.gciatto.ktconstraints.core

/** Classical Boolean disjunction. */
data class BoolOr(
    /** Left operand. */
    val left: BoolExpr,
    /** Right operand. */
    val right: BoolExpr,
) : BoolExpr {
    override val children: List<Expr> get() = listOf(left, right)
}
