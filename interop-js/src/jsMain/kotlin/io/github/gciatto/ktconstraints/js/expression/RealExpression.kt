package io.github.gciatto.ktconstraints.js.expression
import io.github.gciatto.ktconstraints.core.Rational
import io.github.gciatto.ktconstraints.core.real.RealExpr

/** Immutable exact linear real expression; constants come from [real], integers embed through [toReal]. */
@JsExport
class RealExpression internal constructor(
    internal val expr: RealExpr,
) {
    /** Exact sum. */
    fun plus(other: RealExpression): RealExpression = RealExpression(expr + other.expr)

    /** Exact difference. */
    fun minus(other: RealExpression): RealExpression = RealExpression(expr - other.expr)

    /** Scales by an exact constant written `p`, `p/q` or as a decimal such as `0.1`; products stay linear. */
    fun scale(factor: String): RealExpression = RealExpression(expr * Rational.parse(factor))

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
