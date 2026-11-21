package io.github.gciatto.ktconstraints.core

/** Global integer expression equal to the smallest of its non-empty operands. */
class IntMin(
    operands: Collection<IntExpr>,
) : IntExpr {
    /** Detached immutable operands, in declaration order. */
    val operands: List<IntExpr> = buildList { addAll(operands) }

    init {
        require(this.operands.isNotEmpty()) { "min requires at least one operand" }
    }

    override val children: List<Expr> get() = operands

    override fun equals(other: Any?): Boolean = other is IntMin && operands == other.operands

    override fun hashCode(): Int = operands.hashCode()

    override fun toString(): String = "min($operands)"
}
