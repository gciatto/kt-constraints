package io.github.gciatto.ktconstraints.core.real.relation

import io.github.gciatto.ktconstraints.core.Expr
import io.github.gciatto.ktconstraints.core.bool.BoolExpr
import io.github.gciatto.ktconstraints.core.real.RealExpr

/** Strict exact real ordering. */
data class RealLessThan(
    /** Left operand. */
    val left: RealExpr,
    /** Right operand. */
    val right: RealExpr,
) : BoolExpr {
    override val children: List<Expr> get() = listOf(left, right)
}
