package io.github.gciatto.ktconstraints.core.global

import io.github.gciatto.ktconstraints.core.bool.BoolAnd
import io.github.gciatto.ktconstraints.core.bool.BoolExpr
import io.github.gciatto.ktconstraints.core.integer.IntExpr
import io.github.gciatto.ktconstraints.core.integer.IntLiteral
import io.github.gciatto.ktconstraints.core.integer.arithmetic.IntMax
import io.github.gciatto.ktconstraints.core.integer.arithmetic.IntMin
import io.github.gciatto.ktconstraints.core.integer.relation.IntLessOrEqual
import io.github.gciatto.ktconstraints.core.integer.relation.IntLessThan

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
