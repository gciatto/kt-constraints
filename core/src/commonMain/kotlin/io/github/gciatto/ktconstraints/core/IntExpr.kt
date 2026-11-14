package io.github.gciatto.ktconstraints.core

/** Mathematical integer expression. */
interface IntExpr : Expr {
    override val sort: Sort get() = Sort.Integer
}
