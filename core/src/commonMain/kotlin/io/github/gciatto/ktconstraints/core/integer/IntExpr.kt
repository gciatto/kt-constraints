package io.github.gciatto.ktconstraints.core.integer

import io.github.gciatto.ktconstraints.core.Expr
import io.github.gciatto.ktconstraints.core.Sort

/** Mathematical integer expression. */
interface IntExpr : Expr {
    override val sort: Sort get() = Sort.Integer
}
