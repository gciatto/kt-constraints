package io.github.gciatto.ktconstraints.core

/** Fixed-width bit-vector expression with SMT-LIB semantics; values are unsigned integers below 2^[width]. */
interface BitVecExpr : Expr {
    /** Number of bits; positive. */
    val width: Int

    override val sort: Sort get() = Sort.BitVector(width)
}

/** Requires every operand to have the same width, returning it. */
internal fun sameWidth(vararg operands: BitVecExpr): Int {
    val width = operands.first().width
    require(operands.all { it.width == width }) { "Bit-vector operand widths differ: ${operands.map { it.width }}" }
    return width
}
