package io.github.gciatto.ktconstraints.core

/** Bitwise complement (`bvnot`). */
data class BitVecNot(
    /** Operand to complement. */
    val operand: BitVecExpr,
) : BitVecExpr {
    override val width: Int get() = operand.width

    override val children: List<Expr> get() = listOf(operand)
}
