@file:JvmMultifileClass
@file:JvmName("Expressions")

package io.github.gciatto.ktconstraints.dsl

import io.github.gciatto.ktconstraints.core.bool.BoolExpr
import io.github.gciatto.ktconstraints.core.integer.IntExpr
import io.github.gciatto.ktconstraints.core.integer.IntLiteral
import io.github.gciatto.ktconstraints.core.integer.relation.IntLessOrEqual
import io.github.gciatto.ktconstraints.core.integer.relation.IntLessThan
import org.gciatto.kt.math.BigInteger
import kotlin.jvm.JvmMultifileClass
import kotlin.jvm.JvmName

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
