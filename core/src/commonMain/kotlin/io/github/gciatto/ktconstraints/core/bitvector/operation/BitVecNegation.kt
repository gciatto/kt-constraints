package io.github.gciatto.ktconstraints.core.bitvector.operation

import io.github.gciatto.ktconstraints.core.Expr
import io.github.gciatto.ktconstraints.core.bitvector.BitVecExpr

/** Two's complement negation (`bvneg`). */
data class BitVecNegation(
    /** Operand to negate. */
    val operand: BitVecExpr,
) : BitVecExpr {
    override val width: Int get() = operand.width

    override val children: List<Expr> get() = listOf(operand)
}
