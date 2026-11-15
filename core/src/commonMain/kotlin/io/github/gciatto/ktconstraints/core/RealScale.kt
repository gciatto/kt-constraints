package io.github.gciatto.ktconstraints.core

/** Rational constant times a real expression; the only real multiplication, keeping arithmetic linear. */
data class RealScale(
    /** Exact constant factor. */
    val coefficient: Rational,
    /** Scaled operand. */
    val operand: RealExpr,
) : RealExpr {
    override val children: List<Expr> get() = listOf(operand)
}
