package io.github.gciatto.ktconstraints.solve.linear

import io.github.gciatto.ktconstraints.core.Rational

/** Exact affine form `Σ terms[i]·x[i] + constant` over column indices, without zero coefficients. */
internal data class Affine(
    val terms: Map<Int, Rational> = emptyMap(),
    val constant: Rational = Rational.ZERO,
) {
    operator fun plus(other: Affine): Affine {
        val merged = terms.toMutableMap()
        other.terms.forEach { (index, coefficient) -> merged[index] = (merged[index] ?: Rational.ZERO) + coefficient }
        return Affine(merged.filterValues { it != Rational.ZERO }, constant + other.constant)
    }

    operator fun minus(other: Affine): Affine = this + other * Rational.of(-1)

    operator fun times(factor: Rational): Affine =
        if (factor == Rational.ZERO) ZERO else Affine(terms.mapValues { it.value * factor }, constant * factor)

    companion object {
        val ZERO = Affine()

        fun of(index: Int): Affine = Affine(mapOf(index to Rational.ONE))

        fun of(constant: Rational): Affine = Affine(constant = constant)
    }
}
