package io.github.gciatto.ktconstraints.core

/** Global constraint comparing the number of true operands to a non-negative bound. */
class BoolCardinality(
    operands: Collection<BoolExpr>,
    /** Non-negative bound on the number of true operands. */
    val count: Int,
    /** Comparison to apply to the count. */
    val relation: CardinalityRelation,
) : BoolExpr {
    /** Detached immutable operands; repeated operands count repeatedly. */
    val operands: List<BoolExpr> = buildList { addAll(operands) }

    init {
        require(count >= 0) { "Cardinality bound must be non-negative" }
    }

    override val children: List<Expr> get() = operands

    override fun equals(other: Any?): Boolean =
        other is BoolCardinality && operands == other.operands && count == other.count && relation == other.relation

    override fun hashCode(): Int = 31 * (31 * operands.hashCode() + count) + relation.hashCode()

    override fun toString(): String = "cardinality($operands, $relation, $count)"
}
