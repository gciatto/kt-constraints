package io.github.gciatto.ktconstraints.core.bitvector.operation

import io.github.gciatto.ktconstraints.core.Expr
import io.github.gciatto.ktconstraints.core.bitvector.BitVecExpr

/** Bitwise complement (`bvnot`). */
data class BitVecNot(
    /** Operand to complement. */
    val operand: BitVecExpr,
) : BitVecExpr {
    override val width: Int get() = operand.width

    override val children: List<Expr> get() = listOf(operand)
}
