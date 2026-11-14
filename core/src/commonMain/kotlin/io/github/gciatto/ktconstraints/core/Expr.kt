package io.github.gciatto.ktconstraints.core

/** Immutable symbolic expression; evaluation is a solver concern. */
interface Expr {
    /** Sort of this expression. */
    val sort: Sort

    /** Free symbolic variables occurring in this expression. */
    val variables: Set<Variable>
}

/** Boolean expression, directly usable as a constraint. */
interface BoolExpr : Expr {
    override val sort: Sort get() = Sort.Bool
}

/** Mathematical integer expression. */
interface IntExpr : Expr {
    override val sort: Sort get() = Sort.Integer
}
