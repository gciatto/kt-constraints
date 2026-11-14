package io.github.gciatto.ktconstraints.core

import org.gciatto.kt.math.BigInteger

/** Allowed-tuple relation over ordered integer operands; an empty tuple set is false. */
class Table(
    operands: Collection<IntExpr>,
    tuples: Collection<List<BigInteger>>,
) : BoolExpr {
    /** Detached immutable operands. */
    val operands: List<IntExpr> = buildList { addAll(operands) }

    /** Detached immutable rows, each with the same arity as [operands]. */
    val tuples: List<List<BigInteger>> = buildList {
        tuples.forEach { row ->
            require(row.size == this@Table.operands.size) { "Table tuple arity does not match operands" }
            add(buildList { addAll(row) })
        }
    }

    override val children: List<Expr> get() = operands

    override fun equals(other: Any?): Boolean = other is Table && operands == other.operands && tuples == other.tuples

    override fun hashCode(): Int = 31 * operands.hashCode() + tuples.hashCode()

    override fun toString(): String = "table($operands, $tuples)"
}
