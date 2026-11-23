package io.github.gciatto.ktconstraints.core.global.counting

import io.github.gciatto.ktconstraints.core.Expr
import io.github.gciatto.ktconstraints.core.bool.BoolExpr
import io.github.gciatto.ktconstraints.core.integer.IntExpr

/** Global constraint requiring pairwise distinct integer values; true for no operands. */
class AllDifferent(
    operands: Collection<IntExpr>,
) : BoolExpr {
    /** Detached immutable operands, in declaration order. */
    val operands: List<IntExpr> = buildList { addAll(operands) }

    override val children: List<Expr> get() = operands

    override fun equals(other: Any?): Boolean = other is AllDifferent && operands == other.operands

    override fun hashCode(): Int = operands.hashCode()

    override fun toString(): String = "allDifferent($operands)"
}
