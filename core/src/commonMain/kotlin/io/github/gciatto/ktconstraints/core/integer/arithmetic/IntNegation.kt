package io.github.gciatto.ktconstraints.core.integer.arithmetic

import io.github.gciatto.ktconstraints.core.Expr
import io.github.gciatto.ktconstraints.core.integer.IntExpr

/** Exact integer additive inverse. */
data class IntNegation(
    /** Operand to negate. */
    val operand: IntExpr,
) : IntExpr {
    override val children: List<Expr> get() = listOf(operand)
}
