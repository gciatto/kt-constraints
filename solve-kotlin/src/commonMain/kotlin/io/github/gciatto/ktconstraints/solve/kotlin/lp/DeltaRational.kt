package io.github.gciatto.ktconstraints.solve.kotlin.lp

import io.github.gciatto.ktconstraints.core.Rational

/**
 * The number `real + delta·δ` for a positive infinitesimal δ, ordered lexicographically. A strict bound `x < c` becomes
 * the non-strict `x ≤ c − δ`, so the simplex handles strict inequalities exactly.
 */
internal data class DeltaRational(
    val real: Rational,
    val delta: Rational = Rational.ZERO,
) : Comparable<DeltaRational> {
    operator fun plus(other: DeltaRational): DeltaRational = DeltaRational(real + other.real, delta + other.delta)

    operator fun minus(other: DeltaRational): DeltaRational = DeltaRational(real - other.real, delta - other.delta)

    operator fun times(factor: Rational): DeltaRational = DeltaRational(real * factor, delta * factor)

    operator fun div(divisor: Rational): DeltaRational = DeltaRational(real / divisor, delta / divisor)

    override fun compareTo(other: DeltaRational): Int = real.compareTo(other.real).takeIf { it != 0 } ?: delta.compareTo(other.delta)

    /** The rational value for the concrete [epsilon] standing for δ. */
    fun at(epsilon: Rational): Rational = real + delta * epsilon

    companion object {
        val ZERO = DeltaRational(Rational.ZERO)

        fun of(value: Rational): DeltaRational = DeltaRational(value)
    }
}
