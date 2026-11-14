package io.github.gciatto.ktconstraints.core

/** Integer expression selecting a zero-based index from a non-empty array. */
class Element(
    val index: IntExpr,
    values: Collection<IntExpr>,
) : IntExpr {
    /** Detached immutable array values. */
    val values: List<IntExpr> = buildList { addAll(values) }

    init {
        require(this.values.isNotEmpty()) { "Element requires a non-empty array" }
    }

    override val children: List<Expr> get() = listOf(index) + values

    override fun equals(other: Any?): Boolean = other is Element && index == other.index && values == other.values

    override fun hashCode(): Int = 31 * index.hashCode() + values.hashCode()

    override fun toString(): String = "element($index, $values)"
}
