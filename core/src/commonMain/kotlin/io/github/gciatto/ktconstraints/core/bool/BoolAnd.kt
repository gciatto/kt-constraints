package io.github.gciatto.ktconstraints.core.bool
import io.github.gciatto.ktconstraints.core.Expr
import kotlin.jvm.JvmStatic

/** Classical Boolean conjunction. */
data class BoolAnd(
    /** Left operand. */
    val left: BoolExpr,
    /** Right operand. */
    val right: BoolExpr,
) : BoolExpr {
    override val children: List<Expr> get() = listOf(left, right)

    /** Factories. */
    companion object {
        /** Balanced conjunction of [operands], keeping nesting logarithmic; `true` when empty. */
        @JvmStatic
        fun of(operands: List<BoolExpr>): BoolExpr =
            when (operands.size) {
                0 -> BoolLiteral(true)
                1 -> operands.single()
                else -> BoolAnd(of(operands.subList(0, operands.size / 2)), of(operands.subList(operands.size / 2, operands.size)))
            }
    }
}
