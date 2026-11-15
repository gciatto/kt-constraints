package io.github.gciatto.ktconstraints.core

/** Bit-vector comparison of operands of equal width. */
data class BitVecComparison(
    /** Compared relation. */
    val relation: BitVecRelation,
    /** Left operand. */
    val left: BitVecExpr,
    /** Right operand. */
    val right: BitVecExpr,
) : BoolExpr {
    init {
        sameWidth(left, right)
    }

    override val children: List<Expr> get() = listOf(left, right)
}
