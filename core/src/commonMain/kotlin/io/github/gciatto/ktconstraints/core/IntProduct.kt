package io.github.gciatto.ktconstraints.core

/** Exact integer multiplication. */
data class IntProduct(
    /** Left operand. */
    val left: IntExpr,
    /** Right operand. */
    val right: IntExpr,
) : IntExpr {
    override val children: List<Expr> get() = listOf(left, right)
}
