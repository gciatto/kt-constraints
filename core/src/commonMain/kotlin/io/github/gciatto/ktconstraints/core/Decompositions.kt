package io.github.gciatto.ktconstraints.core

/** Building blocks shared by the decompositions of global constraints. */
internal object Decompositions {
    /** `0 ≤ operand < size`. */
    fun inRange(
        operand: IntExpr,
        size: Int,
    ): BoolExpr = BoolAnd(IntLessOrEqual(IntLiteral(0), operand), IntLessThan(operand, IntLiteral(size)))

    /** [operand] clamped into `0 until size`, so an element lookup on it is always defined. */
    fun clamp(
        operand: IntExpr,
        size: Int,
    ): IntExpr = IntMin(listOf(IntMax(listOf(operand, IntLiteral(0))), IntLiteral(size - 1)))
}
