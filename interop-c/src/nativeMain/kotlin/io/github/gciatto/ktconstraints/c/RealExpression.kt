package io.github.gciatto.ktconstraints.c

import io.github.gciatto.ktconstraints.core.evaluation.GroundEvaluator
import io.github.gciatto.ktconstraints.core.real.RealExpr

/** Immutable exact real expression; constants come from [realConstant], integers embed through [IntExpression.toReal]. */
class RealExpression internal constructor(
    internal val expr: RealExpr,
) {
    /** Exact sum. */
    fun plus(other: RealExpression): RealExpression = RealExpression(expr + other.expr)

    /** Exact difference. */
    fun minus(other: RealExpression): RealExpression = RealExpression(expr - other.expr)

    /** Exact product with a ground factor; null when both factors mention variables, which reals cannot express. */
    fun times(other: RealExpression): RealExpression? =
        when {
            expr.variables.isEmpty() -> RealExpression(other.expr * GroundEvaluator.evaluate(expr))
            other.expr.variables.isEmpty() -> RealExpression(expr * GroundEvaluator.evaluate(other.expr))
            else -> null
        }

    /** Exact negation. */
    fun negate(): RealExpression = RealExpression(-expr)

    /** Symbolic equality. */
    fun eq(other: RealExpression): BoolExpression = BoolExpression(expr eq other.expr)

    /** Symbolic disequality. */
    fun neq(other: RealExpression): BoolExpression = BoolExpression(expr neq other.expr)

    /** Symbolic strict ordering. */
    fun lt(other: RealExpression): BoolExpression = BoolExpression(expr lt other.expr)

    /** Symbolic non-strict ordering. */
    fun le(other: RealExpression): BoolExpression = BoolExpression(expr le other.expr)

    /** Symbolic reversed strict ordering. */
    fun gt(other: RealExpression): BoolExpression = BoolExpression(expr gt other.expr)

    /** Symbolic reversed non-strict ordering. */
    fun ge(other: RealExpression): BoolExpression = BoolExpression(expr ge other.expr)
}
