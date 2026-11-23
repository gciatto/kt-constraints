package io.github.gciatto.ktconstraints.core.integer.arithmetic

import io.github.gciatto.ktconstraints.core.Expr
import io.github.gciatto.ktconstraints.core.integer.IntExpr

/** Global integer expression equal to the largest of its non-empty operands. */
class IntMax(
    operands: Collection<IntExpr>,
) : IntExpr {
    /** Detached immutable operands, in declaration order. */
    val operands: List<IntExpr> = buildList { addAll(operands) }

    init {
        require(this.operands.isNotEmpty()) { "max requires at least one operand" }
    }

    override val children: List<Expr> get() = operands

    override fun equals(other: Any?): Boolean = other is IntMax && operands == other.operands

    override fun hashCode(): Int = operands.hashCode()

    override fun toString(): String = "max($operands)"
}
