package io.github.gciatto.ktconstraints.core

/** Exact integer value of a bit-vector, read as unsigned or as two's complement. */
data class BitVecToInt(
    /** Converted operand. */
    val operand: BitVecExpr,
    /** Whether the operand is read in two's complement. */
    val signed: Boolean,
) : IntExpr {
    override val children: List<Expr> get() = listOf(operand)
}
