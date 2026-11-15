package io.github.gciatto.ktconstraints.core

/** Two's complement negation (`bvneg`). */
data class BitVecNegation(
    /** Operand to negate. */
    val operand: BitVecExpr,
) : BitVecExpr {
    override val width: Int get() = operand.width

    override val children: List<Expr> get() = listOf(operand)
}
