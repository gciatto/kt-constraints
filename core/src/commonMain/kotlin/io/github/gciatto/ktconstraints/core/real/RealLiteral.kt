package io.github.gciatto.ktconstraints.core.real

import io.github.gciatto.ktconstraints.core.Rational
import io.github.gciatto.ktconstraints.core.Variable

/** Exact rational real literal. */
data class RealLiteral(
    /** Exact constant value. */
    val value: Rational,
) : RealExpr {
    override val variables: Set<Variable> get() = emptySet()

    override fun toString(): String = value.toString()
}
