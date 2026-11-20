@file:JvmMultifileClass
@file:JvmName("Expressions")

package io.github.gciatto.ktconstraints.dsl

import io.github.gciatto.ktconstraints.core.BoolExpr
import io.github.gciatto.ktconstraints.core.BoolNot
import io.github.gciatto.ktconstraints.core.Rational
import io.github.gciatto.ktconstraints.core.RealEquality
import io.github.gciatto.ktconstraints.core.RealExpr
import io.github.gciatto.ktconstraints.core.RealLiteral
import kotlin.jvm.JvmMultifileClass
import kotlin.jvm.JvmName

/** Creates a symbolic real equality. */
infix fun RealExpr.eq(other: RealExpr): BoolExpr = RealEquality(this, other)

/** Creates a symbolic real equality. with an exact constant. */
infix fun RealExpr.eq(value: Rational): BoolExpr = eq(RealLiteral(value))

/** Creates a symbolic real equality. with an exact machine-integer constant. */
infix fun RealExpr.eq(value: Long): BoolExpr = eq(Rational.of(value))

/** Creates a symbolic real equality. with an exact machine-integer constant. */
infix fun RealExpr.eq(value: Int): BoolExpr = eq(value.toLong())

/** Creates a symbolic real disequality. */
infix fun RealExpr.neq(other: RealExpr): BoolExpr = BoolNot(RealEquality(this, other))

/** Creates a symbolic real disequality. with an exact constant. */
infix fun RealExpr.neq(value: Rational): BoolExpr = neq(RealLiteral(value))

/** Creates a symbolic real disequality. with an exact machine-integer constant. */
infix fun RealExpr.neq(value: Long): BoolExpr = neq(Rational.of(value))

/** Creates a symbolic real disequality. with an exact machine-integer constant. */
infix fun RealExpr.neq(value: Int): BoolExpr = neq(value.toLong())
