package io.github.gciatto.ktconstraints.core.real.arithmetic

import io.github.gciatto.ktconstraints.core.Expr
import io.github.gciatto.ktconstraints.core.Rational
import io.github.gciatto.ktconstraints.core.real.RealExpr

/** Rational constant times a real expression; the only real multiplication, keeping arithmetic linear. */
data class RealScale(
    /** Exact constant factor. */
    val coefficient: Rational,
    /** Scaled operand. */
    val operand: RealExpr,
) : RealExpr {
    override val children: List<Expr> get() = listOf(operand)
}
