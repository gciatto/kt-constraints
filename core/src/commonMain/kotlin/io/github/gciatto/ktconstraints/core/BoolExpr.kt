package io.github.gciatto.ktconstraints.core

/** Boolean expression, directly usable as a constraint. */
interface BoolExpr : Expr {
    override val sort: Sort get() = Sort.Bool
}
