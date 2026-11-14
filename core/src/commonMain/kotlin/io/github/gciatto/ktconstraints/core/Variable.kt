package io.github.gciatto.ktconstraints.core

/** Variable identity is unique within and across modeling scopes; names are merely presentational. */
class VariableId internal constructor(
    private val owner: Any,
    val index: Int,
) {
    override fun equals(other: Any?): Boolean = other is VariableId && owner === other.owner && index == other.index

    override fun hashCode(): Int = 31 * owner.hashCode() + index

    override fun toString(): String = index.toString()
}

/** Symbolic variable belonging to one modeling scope. */
interface Variable : Expr {
    /** Semantic variable identity; independent of [name]. */
    val id: VariableId

    /** Human-friendly variable name. */
    val name: String

    override val variables: Set<Variable> get() = setOf(this)
}

/** Symbolic Boolean variable. */
class BoolVar internal constructor(
    override val id: VariableId,
    override val name: String,
) : BoolExpr,
    Variable {
    override val sort: Sort get() = Sort.Bool

    override fun equals(other: Any?): Boolean = other is BoolVar && id == other.id

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String = name
}

/** Symbolic finite-domain integer variable. */
class IntVar internal constructor(
    override val id: VariableId,
    override val name: String,
    val domain: IntDomain,
) : IntExpr,
    Variable {
    override val sort: Sort get() = Sort.Integer

    override fun equals(other: Any?): Boolean = other is IntVar && id == other.id

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String = name
}
