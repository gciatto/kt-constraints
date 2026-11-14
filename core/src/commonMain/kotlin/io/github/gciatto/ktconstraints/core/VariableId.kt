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
