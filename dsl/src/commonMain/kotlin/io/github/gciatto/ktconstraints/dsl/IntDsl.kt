package io.github.gciatto.ktconstraints.dsl

import io.github.gciatto.ktconstraints.core.BoolExpr
import io.github.gciatto.ktconstraints.core.IntEquality
import io.github.gciatto.ktconstraints.core.IntExpr
import io.github.gciatto.ktconstraints.core.IntLiteral
import io.github.gciatto.ktconstraints.core.IntSum

/** Creates a symbolic sum, not an eager numeric addition. */
operator fun IntExpr.plus(other: IntExpr): IntExpr = IntSum(this, other)

/** Creates a symbolic sum with a literal. */
operator fun IntExpr.plus(value: Int): IntExpr = IntSum(this, IntLiteral(value.toLong()))

/** Creates a symbolic sum with a literal. */
operator fun IntExpr.plus(value: Long): IntExpr = IntSum(this, IntLiteral(value))

/** Creates a symbolic equality; Kotlin == always remains structural equality. */
infix fun IntExpr.eq(other: IntExpr): BoolExpr = IntEquality(this, other)

/** Creates a symbolic equality against an integer literal. */
infix fun IntExpr.eq(value: Int): BoolExpr = IntEquality(this, IntLiteral(value.toLong()))
