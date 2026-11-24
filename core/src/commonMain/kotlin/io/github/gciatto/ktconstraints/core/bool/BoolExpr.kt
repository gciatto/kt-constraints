package io.github.gciatto.ktconstraints.core.bool

import io.github.gciatto.ktconstraints.core.Expr
import io.github.gciatto.ktconstraints.core.Sort

/** Boolean expression, directly usable as a constraint. */
interface BoolExpr : Expr {
    override val sort: Sort get() = Sort.Bool

    /** Creates a symbolic Boolean conjunction. */
    infix fun and(other: BoolExpr): BoolExpr = BoolAnd(this, other)

    /** Creates a symbolic Boolean disjunction. */
    infix fun or(other: BoolExpr): BoolExpr = BoolOr(this, other)

    /** Creates a symbolic Boolean implication. */
    infix fun implies(other: BoolExpr): BoolExpr = BoolImplies(this, other)

    /** Creates a symbolic Boolean equivalence. */
    infix fun eq(other: BoolExpr): BoolExpr = BoolEquality(this, other)

    /** Creates a symbolic Boolean inequality. */
    infix fun neq(other: BoolExpr): BoolExpr = BoolNot(BoolEquality(this, other))

    /** Creates a symbolic Boolean negation. */
    operator fun not(): BoolExpr = BoolNot(this)

    /** 1 when this holds, 0 otherwise. */
    fun toInt(): BoolToInt = BoolToInt(this)
}
