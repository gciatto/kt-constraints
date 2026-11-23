package io.github.gciatto.ktconstraints.c

import io.github.gciatto.ktconstraints.core.bool.BoolExpr
import io.github.gciatto.ktconstraints.dsl.and
import io.github.gciatto.ktconstraints.dsl.eq
import io.github.gciatto.ktconstraints.dsl.implies
import io.github.gciatto.ktconstraints.dsl.not
import io.github.gciatto.ktconstraints.dsl.or

/** Immutable Boolean expression, usable as a constraint. */
class BoolExpression internal constructor(
    internal val expr: BoolExpr,
) {
    /** Conjunction. */
    fun and(other: BoolExpression): BoolExpression = BoolExpression(expr and other.expr)

    /** Disjunction. */
    fun or(other: BoolExpression): BoolExpression = BoolExpression(expr or other.expr)

    /** Implication. */
    fun implies(other: BoolExpression): BoolExpression = BoolExpression(expr implies other.expr)

    /** Equivalence. */
    fun iff(other: BoolExpression): BoolExpression = BoolExpression(expr eq other.expr)

    /** Negation. */
    fun not(): BoolExpression = BoolExpression(!expr)
}
