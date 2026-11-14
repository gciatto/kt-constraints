package io.github.gciatto.ktconstraints.core

/** Exact integer additive inverse. */
data class IntNegation(
    /** Operand to negate. */
    val operand: IntExpr,
) : IntExpr {
    override val children: List<Expr> get() = listOf(operand)
}
