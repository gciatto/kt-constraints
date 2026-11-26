package io.github.gciatto.ktconstraints.c

import io.github.gciatto.ktconstraints.core.integer.IntExpr

/** Immutable exact integer expression; constants come from [constant] and [constantOf]. */
class IntExpression internal constructor(
    internal val expr: IntExpr,
) {
    /** Exact sum. */
    fun plus(other: IntExpression): IntExpression = IntExpression(expr + other.expr)

    /** Exact difference. */
    fun minus(other: IntExpression): IntExpression = IntExpression(expr - other.expr)

    /** Exact product. */
    fun times(other: IntExpression): IntExpression = IntExpression(expr * other.expr)

    /** Exact negation. */
    fun negate(): IntExpression = IntExpression(-expr)

    /** Embeds this exact integer expression into the real sort. */
    fun toReal(): RealExpression = RealExpression(expr.asRealExpr())

    /** Symbolic equality. */
    fun eq(other: IntExpression): BoolExpression = BoolExpression(expr eq other.expr)

    /** Symbolic disequality. */
    fun neq(other: IntExpression): BoolExpression = BoolExpression(expr neq other.expr)

    /** Symbolic strict ordering. */
    fun lt(other: IntExpression): BoolExpression = BoolExpression(expr lt other.expr)

    /** Symbolic non-strict ordering. */
    fun le(other: IntExpression): BoolExpression = BoolExpression(expr le other.expr)

    /** Symbolic reversed strict ordering. */
    fun gt(other: IntExpression): BoolExpression = BoolExpression(expr gt other.expr)

    /** Symbolic reversed non-strict ordering. */
    fun ge(other: IntExpression): BoolExpression = BoolExpression(expr ge other.expr)
}
