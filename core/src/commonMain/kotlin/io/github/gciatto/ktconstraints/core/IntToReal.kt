package io.github.gciatto.ktconstraints.core

/** Exact embedding of an integer expression into the reals. */
data class IntToReal(
    /** Integer operand. */
    val operand: IntExpr,
) : RealExpr {
    override val children: List<Expr> get() = listOf(operand)
}
