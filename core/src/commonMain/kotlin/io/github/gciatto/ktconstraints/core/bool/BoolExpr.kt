package io.github.gciatto.ktconstraints.core.bool

import io.github.gciatto.ktconstraints.core.Expr
import io.github.gciatto.ktconstraints.core.Sort

/** Boolean expression, directly usable as a constraint. */
interface BoolExpr : Expr {
    override val sort: Sort get() = Sort.Bool
}
