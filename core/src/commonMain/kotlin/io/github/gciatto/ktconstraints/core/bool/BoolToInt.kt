package io.github.gciatto.ktconstraints.core.bool

import io.github.gciatto.ktconstraints.core.Expr
import io.github.gciatto.ktconstraints.core.integer.IntExpr

/** Integer 1 when the Boolean operand holds, 0 otherwise. */
data class BoolToInt(
    /** Boolean operand. */
    val operand: BoolExpr,
) : IntExpr {
    override val children: List<Expr> get() = listOf(operand)
}
