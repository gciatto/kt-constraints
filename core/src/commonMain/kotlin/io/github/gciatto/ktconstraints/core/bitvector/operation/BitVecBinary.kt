package io.github.gciatto.ktconstraints.core.bitvector.operation

import io.github.gciatto.ktconstraints.core.Expr
import io.github.gciatto.ktconstraints.core.bitvector.BitVecExpr
import io.github.gciatto.ktconstraints.core.bitvector.sameWidth

/** Binary bit-vector operation on operands of equal width. */
data class BitVecBinary(
    /** Applied operation. */
    val operator: BitVecOperator,
    /** Left operand. */
    val left: BitVecExpr,
    /** Right operand (the shift amount for shifts). */
    val right: BitVecExpr,
) : BitVecExpr {
    override val width: Int = sameWidth(left, right)

    override val children: List<Expr> get() = listOf(left, right)
}
