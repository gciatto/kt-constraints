@file:JvmMultifileClass
@file:JvmName("Expressions")

package io.github.gciatto.ktconstraints.dsl

import io.github.gciatto.ktconstraints.core.Rational
import io.github.gciatto.ktconstraints.core.bool.BoolExpr
import io.github.gciatto.ktconstraints.core.real.RealExpr
import io.github.gciatto.ktconstraints.core.real.RealLiteral
import io.github.gciatto.ktconstraints.core.real.relation.RealLessOrEqual
import io.github.gciatto.ktconstraints.core.real.relation.RealLessThan
import kotlin.jvm.JvmMultifileClass
import kotlin.jvm.JvmName

/** Creates a symbolic real strict ordering. */
infix fun RealExpr.lt(other: RealExpr): BoolExpr = RealLessThan(this, other)

/** Creates a symbolic real strict ordering. with an exact constant. */
infix fun RealExpr.lt(value: Rational): BoolExpr = lt(RealLiteral(value))

/** Creates a symbolic real strict ordering. with an exact machine-integer constant. */
infix fun RealExpr.lt(value: Long): BoolExpr = lt(Rational.of(value))

/** Creates a symbolic real strict ordering. with an exact machine-integer constant. */
infix fun RealExpr.lt(value: Int): BoolExpr = lt(value.toLong())

/** Creates a symbolic real non-strict ordering. */
infix fun RealExpr.le(other: RealExpr): BoolExpr = RealLessOrEqual(this, other)

/** Creates a symbolic real non-strict ordering. with an exact constant. */
infix fun RealExpr.le(value: Rational): BoolExpr = le(RealLiteral(value))

/** Creates a symbolic real non-strict ordering. with an exact machine-integer constant. */
infix fun RealExpr.le(value: Long): BoolExpr = le(Rational.of(value))

/** Creates a symbolic real non-strict ordering. with an exact machine-integer constant. */
infix fun RealExpr.le(value: Int): BoolExpr = le(value.toLong())

/** Creates a symbolic reversed real strict ordering. */
infix fun RealExpr.gt(other: RealExpr): BoolExpr = RealLessThan(other, this)

/** Creates a symbolic reversed real strict ordering. with an exact constant. */
infix fun RealExpr.gt(value: Rational): BoolExpr = gt(RealLiteral(value))

/** Creates a symbolic reversed real strict ordering. with an exact machine-integer constant. */
infix fun RealExpr.gt(value: Long): BoolExpr = gt(Rational.of(value))

/** Creates a symbolic reversed real strict ordering. with an exact machine-integer constant. */
infix fun RealExpr.gt(value: Int): BoolExpr = gt(value.toLong())

/** Creates a symbolic reversed real non-strict ordering. */
infix fun RealExpr.ge(other: RealExpr): BoolExpr = RealLessOrEqual(other, this)

/** Creates a symbolic reversed real non-strict ordering. with an exact constant. */
infix fun RealExpr.ge(value: Rational): BoolExpr = ge(RealLiteral(value))

/** Creates a symbolic reversed real non-strict ordering. with an exact machine-integer constant. */
infix fun RealExpr.ge(value: Long): BoolExpr = ge(Rational.of(value))

/** Creates a symbolic reversed real non-strict ordering. with an exact machine-integer constant. */
infix fun RealExpr.ge(value: Int): BoolExpr = ge(value.toLong())
