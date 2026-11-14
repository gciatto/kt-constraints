package io.github.gciatto.ktconstraints.core

/** Exact integer addition. */
data class IntSum(
    /** Left operand. */
    val left: IntExpr,
    /** Right operand. */
    val right: IntExpr,
) : IntExpr {
    override val children: List<Expr> get() = listOf(left, right)
}
