package io.github.gciatto.ktconstraints.core.bool

import io.github.gciatto.ktconstraints.core.Sort
import io.github.gciatto.ktconstraints.core.Variable
import io.github.gciatto.ktconstraints.core.VariableId

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
