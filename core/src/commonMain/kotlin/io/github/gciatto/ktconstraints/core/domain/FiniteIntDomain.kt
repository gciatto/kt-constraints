package io.github.gciatto.ktconstraints.core.domain

internal data class FiniteIntDomain(
    override val intervals: List<IntInterval>,
) : IntDomain {
    override fun toString(): String = intervals.joinToString(prefix = "{", postfix = "}")
}
