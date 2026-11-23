package io.github.gciatto.ktconstraints.core.integer

import io.github.gciatto.ktconstraints.core.Sort
import io.github.gciatto.ktconstraints.core.Variable
import io.github.gciatto.ktconstraints.core.VariableId
import io.github.gciatto.ktconstraints.core.domain.IntDomain

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
