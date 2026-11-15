package io.github.gciatto.ktconstraints.core

/** Symbolic unbounded real variable; bounds are ordinary constraints. */
class RealVar internal constructor(
    override val id: VariableId,
    override val name: String,
) : RealExpr,
    Variable {
    override val sort: Sort get() = Sort.Real

    override fun equals(other: Any?): Boolean = other is RealVar && id == other.id

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String = name
}
