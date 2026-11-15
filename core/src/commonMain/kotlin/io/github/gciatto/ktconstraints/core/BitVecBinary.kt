package io.github.gciatto.ktconstraints.core

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
