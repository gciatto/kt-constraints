package io.github.gciatto.ktconstraints.core.bool

import io.github.gciatto.ktconstraints.core.Expr

/** Classical Boolean negation. */
data class BoolNot(
    /** Operand to negate. */
    val operand: BoolExpr,
) : BoolExpr {
    override val children: List<Expr> get() = listOf(operand)
}
