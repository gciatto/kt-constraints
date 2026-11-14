package io.github.gciatto.ktconstraints.core

/** Symbolic finite-domain integer variable. */
class IntVar internal constructor(
    override val id: VariableId,
    override val name: String,
    /** Allowed exact integer values. */
    val domain: IntDomain,
) : IntExpr,
    Variable {
    override val sort: Sort get() = Sort.Integer

    override fun equals(other: Any?): Boolean = other is IntVar && id == other.id

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String = name
}
