package io.github.gciatto.ktconstraints.core

/** Integer 1 when the Boolean operand holds, 0 otherwise. */
data class BoolToInt(
    /** Boolean operand. */
    val operand: BoolExpr,
) : IntExpr {
    override val children: List<Expr> get() = listOf(operand)
}
