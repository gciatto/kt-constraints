@file:JvmMultifileClass
@file:JvmName("Expressions")

package io.github.gciatto.ktconstraints.dsl

import io.github.gciatto.ktconstraints.core.bool.BoolAnd
import io.github.gciatto.ktconstraints.core.bool.BoolEquality
import io.github.gciatto.ktconstraints.core.bool.BoolExpr
import io.github.gciatto.ktconstraints.core.bool.BoolImplies
import io.github.gciatto.ktconstraints.core.bool.BoolNot
import io.github.gciatto.ktconstraints.core.bool.BoolOr
import kotlin.jvm.JvmMultifileClass
import kotlin.jvm.JvmName

/** Creates a symbolic Boolean conjunction. */
infix fun BoolExpr.and(other: BoolExpr): BoolExpr = BoolAnd(this, other)

/** Creates a symbolic Boolean disjunction. */
infix fun BoolExpr.or(other: BoolExpr): BoolExpr = BoolOr(this, other)

/** Creates a symbolic Boolean implication. */
infix fun BoolExpr.implies(other: BoolExpr): BoolExpr = BoolImplies(this, other)

/** Creates a symbolic Boolean equivalence. */
infix fun BoolExpr.eq(other: BoolExpr): BoolExpr = BoolEquality(this, other)

/** Creates a symbolic Boolean inequality. */
infix fun BoolExpr.neq(other: BoolExpr): BoolExpr = BoolNot(BoolEquality(this, other))

/** Creates a symbolic Boolean negation. */
operator fun BoolExpr.not(): BoolExpr = BoolNot(this)
