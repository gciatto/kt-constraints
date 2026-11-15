package io.github.gciatto.ktconstraints.core

/** Exact mathematical real expression; only linear operations are representable, so ground values stay rational. */
interface RealExpr : Expr {
    override val sort: Sort get() = Sort.Real
}
