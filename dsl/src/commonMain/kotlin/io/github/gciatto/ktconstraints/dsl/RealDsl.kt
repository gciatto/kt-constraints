@file:JvmMultifileClass
@file:JvmName("Expressions")

package io.github.gciatto.ktconstraints.dsl

import io.github.gciatto.ktconstraints.core.IntExpr
import io.github.gciatto.ktconstraints.core.IntToReal
import io.github.gciatto.ktconstraints.core.Rational
import io.github.gciatto.ktconstraints.core.RealDifference
import io.github.gciatto.ktconstraints.core.RealExpr
import io.github.gciatto.ktconstraints.core.RealLiteral
import io.github.gciatto.ktconstraints.core.RealNegation
import io.github.gciatto.ktconstraints.core.RealScale
import io.github.gciatto.ktconstraints.core.RealSum
import kotlin.jvm.JvmMultifileClass
import kotlin.jvm.JvmName

/** Creates an exact real sum. */
operator fun RealExpr.plus(other: RealExpr): RealExpr = RealSum(this, other)

/** Creates an exact real sum. with an exact constant. */
operator fun RealExpr.plus(value: Rational): RealExpr = plus(RealLiteral(value))

/** Creates an exact real sum. with an exact machine-integer constant. */
operator fun RealExpr.plus(value: Long): RealExpr = plus(Rational.of(value))

/** Creates an exact real sum. with an exact machine-integer constant. */
operator fun RealExpr.plus(value: Int): RealExpr = plus(value.toLong())

/** Creates an exact real difference. */
operator fun RealExpr.minus(other: RealExpr): RealExpr = RealDifference(this, other)

/** Creates an exact real difference. with an exact constant. */
operator fun RealExpr.minus(value: Rational): RealExpr = minus(RealLiteral(value))

/** Creates an exact real difference. with an exact machine-integer constant. */
operator fun RealExpr.minus(value: Long): RealExpr = minus(Rational.of(value))

/** Creates an exact real difference. with an exact machine-integer constant. */
operator fun RealExpr.minus(value: Int): RealExpr = minus(value.toLong())

/** Scales by an exact constant; products of real variables are not linear and are not offered. */
operator fun RealExpr.times(value: Rational): RealExpr = RealScale(value, this)

/** Scales by an exact machine-integer constant. */
operator fun RealExpr.times(value: Long): RealExpr = times(Rational.of(value))

/** Scales by an exact machine-integer constant. */
operator fun RealExpr.times(value: Int): RealExpr = times(value.toLong())

/** Creates an exact real negation. */
operator fun RealExpr.unaryMinus(): RealExpr = RealNegation(this)

/** Embeds an integer expression into the reals explicitly. */
fun IntExpr.toReal(): RealExpr = IntToReal(this)
