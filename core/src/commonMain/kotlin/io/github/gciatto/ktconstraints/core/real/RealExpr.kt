package io.github.gciatto.ktconstraints.core.real

import io.github.gciatto.ktconstraints.core.Expr
import io.github.gciatto.ktconstraints.core.Rational
import io.github.gciatto.ktconstraints.core.Sort
import io.github.gciatto.ktconstraints.core.bool.BoolExpr
import io.github.gciatto.ktconstraints.core.bool.BoolNot
import io.github.gciatto.ktconstraints.core.real.arithmetic.RealDifference
import io.github.gciatto.ktconstraints.core.real.arithmetic.RealNegation
import io.github.gciatto.ktconstraints.core.real.arithmetic.RealScale
import io.github.gciatto.ktconstraints.core.real.arithmetic.RealSum
import io.github.gciatto.ktconstraints.core.real.relation.RealEquality
import io.github.gciatto.ktconstraints.core.real.relation.RealLessOrEqual
import io.github.gciatto.ktconstraints.core.real.relation.RealLessThan

/** Exact mathematical real expression; only linear operations are representable, so ground values stay rational. */
interface RealExpr : Expr {
    override val sort: Sort get() = Sort.Real

    /** Creates an exact real sum. */
    operator fun plus(other: RealExpr): RealExpr = RealSum(this, other)

    /** Creates an exact real sum with an exact constant. */
    operator fun plus(value: Rational): RealExpr = plus(RealLiteral(value))

    /** Creates an exact real sum with an exact machine-integer constant. */
    operator fun plus(value: Long): RealExpr = plus(Rational.of(value))

    /** Creates an exact real sum with an exact machine-integer constant. */
    operator fun plus(value: Int): RealExpr = plus(value.toLong())

    /** Creates an exact real difference. */
    operator fun minus(other: RealExpr): RealExpr = RealDifference(this, other)

    /** Creates an exact real difference with an exact constant. */
    operator fun minus(value: Rational): RealExpr = minus(RealLiteral(value))

    /** Creates an exact real difference with an exact machine-integer constant. */
    operator fun minus(value: Long): RealExpr = minus(Rational.of(value))

    /** Creates an exact real difference with an exact machine-integer constant. */
    operator fun minus(value: Int): RealExpr = minus(value.toLong())

    /** Creates a symbolic real equality. */
    infix fun eq(other: RealExpr): BoolExpr = RealEquality(this, other)

    /** Creates a symbolic real equality with an exact constant. */
    infix fun eq(value: Rational): BoolExpr = eq(RealLiteral(value))

    /** Creates a symbolic real equality with an exact machine-integer constant. */
    infix fun eq(value: Long): BoolExpr = eq(Rational.of(value))

    /** Creates a symbolic real equality with an exact machine-integer constant. */
    infix fun eq(value: Int): BoolExpr = eq(value.toLong())

    /** Creates a symbolic real disequality. */
    infix fun neq(other: RealExpr): BoolExpr = BoolNot(RealEquality(this, other))

    /** Creates a symbolic real disequality with an exact constant. */
    infix fun neq(value: Rational): BoolExpr = neq(RealLiteral(value))

    /** Creates a symbolic real disequality with an exact machine-integer constant. */
    infix fun neq(value: Long): BoolExpr = neq(Rational.of(value))

    /** Creates a symbolic real disequality with an exact machine-integer constant. */
    infix fun neq(value: Int): BoolExpr = neq(value.toLong())

    /** Creates a symbolic real strict ordering. */
    infix fun lt(other: RealExpr): BoolExpr = RealLessThan(this, other)

    /** Creates a symbolic real strict ordering with an exact constant. */
    infix fun lt(value: Rational): BoolExpr = lt(RealLiteral(value))

    /** Creates a symbolic real strict ordering with an exact machine-integer constant. */
    infix fun lt(value: Long): BoolExpr = lt(Rational.of(value))

    /** Creates a symbolic real strict ordering with an exact machine-integer constant. */
    infix fun lt(value: Int): BoolExpr = lt(value.toLong())

    /** Creates a symbolic real non-strict ordering. */
    infix fun le(other: RealExpr): BoolExpr = RealLessOrEqual(this, other)

    /** Creates a symbolic real non-strict ordering with an exact constant. */
    infix fun le(value: Rational): BoolExpr = le(RealLiteral(value))

    /** Creates a symbolic real non-strict ordering with an exact machine-integer constant. */
    infix fun le(value: Long): BoolExpr = le(Rational.of(value))

    /** Creates a symbolic real non-strict ordering with an exact machine-integer constant. */
    infix fun le(value: Int): BoolExpr = le(value.toLong())

    /** Creates a symbolic reversed real strict ordering. */
    infix fun gt(other: RealExpr): BoolExpr = RealLessThan(other, this)

    /** Creates a symbolic reversed real strict ordering with an exact constant. */
    infix fun gt(value: Rational): BoolExpr = gt(RealLiteral(value))

    /** Creates a symbolic reversed real strict ordering with an exact machine-integer constant. */
    infix fun gt(value: Long): BoolExpr = gt(Rational.of(value))

    /** Creates a symbolic reversed real strict ordering with an exact machine-integer constant. */
    infix fun gt(value: Int): BoolExpr = gt(value.toLong())

    /** Creates a symbolic reversed real non-strict ordering. */
    infix fun ge(other: RealExpr): BoolExpr = RealLessOrEqual(other, this)

    /** Creates a symbolic reversed real non-strict ordering with an exact constant. */
    infix fun ge(value: Rational): BoolExpr = ge(RealLiteral(value))

    /** Creates a symbolic reversed real non-strict ordering with an exact machine-integer constant. */
    infix fun ge(value: Long): BoolExpr = ge(Rational.of(value))

    /** Creates a symbolic reversed real non-strict ordering with an exact machine-integer constant. */
    infix fun ge(value: Int): BoolExpr = ge(value.toLong())

    /** Scales by an exact constant; products of real variables are not linear and are not offered. */
    operator fun times(value: Rational): RealExpr = RealScale(value, this)

    /** Scales by an exact machine-integer constant. */
    operator fun times(value: Long): RealExpr = times(Rational.of(value))

    /** Scales by an exact machine-integer constant. */
    operator fun times(value: Int): RealExpr = times(value.toLong())

    /** Creates an exact real negation. */
    operator fun unaryMinus(): RealExpr = RealNegation(this)
}
