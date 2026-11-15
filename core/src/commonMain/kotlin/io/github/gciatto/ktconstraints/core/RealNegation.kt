package io.github.gciatto.ktconstraints.core

/** Exact real additive inverse. */
data class RealNegation(
    /** Operand to negate. */
    val operand: RealExpr,
) : RealExpr {
    override val children: List<Expr> get() = listOf(operand)
}
