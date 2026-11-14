package io.github.gciatto.ktconstraints.dsl

import io.github.gciatto.ktconstraints.core.IntDifference
import io.github.gciatto.ktconstraints.core.IntExpr
import io.github.gciatto.ktconstraints.core.IntLiteral
import io.github.gciatto.ktconstraints.core.IntNegation
import io.github.gciatto.ktconstraints.core.IntProduct
import io.github.gciatto.ktconstraints.core.IntSum
import org.gciatto.kt.math.BigInteger

/** Creates a symbolic integer sum. */
operator fun IntExpr.plus(other: IntExpr): IntExpr = IntSum(this, other)

/** Creates a symbolic integer sum with an exact literal. */
operator fun IntExpr.plus(value: Int): IntExpr = plus(IntLiteral(value))

/** Creates a symbolic integer sum with an exact literal. */
operator fun IntExpr.plus(value: Long): IntExpr = plus(IntLiteral(value))

/** Creates a symbolic integer sum with an exact literal. */
operator fun IntExpr.plus(value: BigInteger): IntExpr = plus(IntLiteral(value))

/** Creates a symbolic integer difference. */
operator fun IntExpr.minus(other: IntExpr): IntExpr = IntDifference(this, other)

/** Creates a symbolic integer difference with an exact literal. */
operator fun IntExpr.minus(value: Int): IntExpr = minus(IntLiteral(value))

/** Creates a symbolic integer difference with an exact literal. */
operator fun IntExpr.minus(value: Long): IntExpr = minus(IntLiteral(value))

/** Creates a symbolic integer difference with an exact literal. */
operator fun IntExpr.minus(value: BigInteger): IntExpr = minus(IntLiteral(value))

/** Creates a symbolic integer product. */
operator fun IntExpr.times(other: IntExpr): IntExpr = IntProduct(this, other)

/** Creates a symbolic integer product with an exact literal. */
operator fun IntExpr.times(value: Int): IntExpr = times(IntLiteral(value))

/** Creates a symbolic integer product with an exact literal. */
operator fun IntExpr.times(value: Long): IntExpr = times(IntLiteral(value))

/** Creates a symbolic integer product with an exact literal. */
operator fun IntExpr.times(value: BigInteger): IntExpr = times(IntLiteral(value))

/** Creates an exact symbolic additive inverse. */
operator fun IntExpr.unaryMinus(): IntExpr = IntNegation(this)
