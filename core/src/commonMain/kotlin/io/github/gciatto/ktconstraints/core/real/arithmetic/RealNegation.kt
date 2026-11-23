package io.github.gciatto.ktconstraints.core.real.arithmetic

import io.github.gciatto.ktconstraints.core.Expr
import io.github.gciatto.ktconstraints.core.real.RealExpr

/** Exact real additive inverse. */
data class RealNegation(
    /** Operand to negate. */
    val operand: RealExpr,
) : RealExpr {
    override val children: List<Expr> get() = listOf(operand)
}
