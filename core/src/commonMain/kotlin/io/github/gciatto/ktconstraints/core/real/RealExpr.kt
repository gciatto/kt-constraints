package io.github.gciatto.ktconstraints.core.real

import io.github.gciatto.ktconstraints.core.Expr
import io.github.gciatto.ktconstraints.core.Sort

/** Exact mathematical real expression; only linear operations are representable, so ground values stay rational. */
interface RealExpr : Expr {
    override val sort: Sort get() = Sort.Real
}
