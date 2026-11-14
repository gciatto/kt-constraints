package io.github.gciatto.ktconstraints.core

/** Classical Boolean negation. */
data class BoolNot(
    /** Operand to negate. */
    val operand: BoolExpr,
) : BoolExpr {
    override val children: List<Expr> get() = listOf(operand)
}
