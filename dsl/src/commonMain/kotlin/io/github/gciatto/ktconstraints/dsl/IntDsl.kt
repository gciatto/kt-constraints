package io.github.gciatto.ktconstraints.dsl

import io.github.gciatto.ktconstraints.core.BoolExpr
import io.github.gciatto.ktconstraints.core.BoolNot
import io.github.gciatto.ktconstraints.core.IntDifference
import io.github.gciatto.ktconstraints.core.IntEquality
import io.github.gciatto.ktconstraints.core.IntExpr
import io.github.gciatto.ktconstraints.core.IntLessOrEqual
import io.github.gciatto.ktconstraints.core.IntLessThan
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

/** Creates a symbolic integer equality. */
infix fun IntExpr.eq(other: IntExpr): BoolExpr = IntEquality(this, other)

/** Creates a symbolic integer equality with an exact literal. */
infix fun IntExpr.eq(value: Int): BoolExpr = eq(IntLiteral(value))

/** Creates a symbolic integer equality with an exact literal. */
infix fun IntExpr.eq(value: Long): BoolExpr = eq(IntLiteral(value))

/** Creates a symbolic integer equality with an exact literal. */
infix fun IntExpr.eq(value: BigInteger): BoolExpr = eq(IntLiteral(value))

/** Creates a symbolic integer inequality. */
infix fun IntExpr.neq(other: IntExpr): BoolExpr = BoolNot(IntEquality(this, other))

/** Creates a symbolic integer inequality with an exact literal. */
infix fun IntExpr.neq(value: Int): BoolExpr = neq(IntLiteral(value))

/** Creates a symbolic integer inequality with an exact literal. */
infix fun IntExpr.neq(value: Long): BoolExpr = neq(IntLiteral(value))

/** Creates a symbolic integer inequality with an exact literal. */
infix fun IntExpr.neq(value: BigInteger): BoolExpr = neq(IntLiteral(value))

/** Creates a symbolic integer strict ordering. */
infix fun IntExpr.lt(other: IntExpr): BoolExpr = IntLessThan(this, other)

/** Creates a symbolic integer strict ordering with an exact literal. */
infix fun IntExpr.lt(value: Int): BoolExpr = lt(IntLiteral(value))

/** Creates a symbolic integer strict ordering with an exact literal. */
infix fun IntExpr.lt(value: Long): BoolExpr = lt(IntLiteral(value))

/** Creates a symbolic integer strict ordering with an exact literal. */
infix fun IntExpr.lt(value: BigInteger): BoolExpr = lt(IntLiteral(value))

/** Creates a symbolic integer non-strict ordering. */
infix fun IntExpr.le(other: IntExpr): BoolExpr = IntLessOrEqual(this, other)

/** Creates a symbolic integer non-strict ordering with an exact literal. */
infix fun IntExpr.le(value: Int): BoolExpr = le(IntLiteral(value))

/** Creates a symbolic integer non-strict ordering with an exact literal. */
infix fun IntExpr.le(value: Long): BoolExpr = le(IntLiteral(value))

/** Creates a symbolic integer non-strict ordering with an exact literal. */
infix fun IntExpr.le(value: BigInteger): BoolExpr = le(IntLiteral(value))

/** Creates a symbolic integer strict reverse ordering. */
infix fun IntExpr.gt(other: IntExpr): BoolExpr = IntLessThan(other, this)

/** Creates a symbolic integer strict reverse ordering with an exact literal. */
infix fun IntExpr.gt(value: Int): BoolExpr = gt(IntLiteral(value))

/** Creates a symbolic integer strict reverse ordering with an exact literal. */
infix fun IntExpr.gt(value: Long): BoolExpr = gt(IntLiteral(value))

/** Creates a symbolic integer strict reverse ordering with an exact literal. */
infix fun IntExpr.gt(value: BigInteger): BoolExpr = gt(IntLiteral(value))

/** Creates a symbolic integer non-strict reverse ordering. */
infix fun IntExpr.ge(other: IntExpr): BoolExpr = IntLessOrEqual(other, this)

/** Creates a symbolic integer non-strict reverse ordering with an exact literal. */
infix fun IntExpr.ge(value: Int): BoolExpr = ge(IntLiteral(value))

/** Creates a symbolic integer non-strict reverse ordering with an exact literal. */
infix fun IntExpr.ge(value: Long): BoolExpr = ge(IntLiteral(value))

/** Creates a symbolic integer non-strict reverse ordering with an exact literal. */
infix fun IntExpr.ge(value: BigInteger): BoolExpr = ge(IntLiteral(value))

/** Creates an exact symbolic additive inverse. */
operator fun IntExpr.unaryMinus(): IntExpr = IntNegation(this)
