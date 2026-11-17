package io.github.gciatto.ktconstraints.solve.mip

import org.gciatto.kt.math.BigInteger

/** Exact affine form `Σ coefficient · variable + constant` over program variable indices, merged by index. */
internal data class MipAffine(
    val terms: Map<Int, BigInteger> = emptyMap(),
    val constant: BigInteger = BigInteger.ZERO,
) {
    operator fun plus(other: MipAffine): MipAffine {
        val merged = terms.toMutableMap()
        other.terms.forEach { (index, coefficient) -> merged[index] = (merged[index] ?: BigInteger.ZERO) + coefficient }
        return MipAffine(merged.filterValues { it.signum != 0 }, constant + other.constant)
    }

    operator fun minus(other: MipAffine): MipAffine = this + other * MINUS_ONE

    operator fun times(factor: BigInteger): MipAffine =
        MipAffine(terms.mapValues { it.value * factor }.filterValues { it.signum != 0 }, constant * factor)

    /** Exact range over [variables]. */
    fun min(variables: List<MipVariable>): BigInteger =
        terms.entries.fold(constant) { total, (index, c) -> total + c * if (c.signum > 0) variables[index].min else variables[index].max }

    /** Exact range over [variables]. */
    fun max(variables: List<MipVariable>): BigInteger =
        terms.entries.fold(constant) { total, (index, c) -> total + c * if (c.signum > 0) variables[index].max else variables[index].min }

    /** Largest absolute value accumulated while evaluating this form over [variables]. */
    fun magnitude(variables: List<MipVariable>): BigInteger =
        terms.entries.fold(constant.absoluteValue) { total, (index, c) ->
            total + c.absoluteValue * variables[index].min.absoluteValue.max(variables[index].max.absoluteValue)
        }

    companion object {
        val MINUS_ONE: BigInteger = -BigInteger.ONE
        val ZERO = MipAffine()
        val ONE = MipAffine(constant = BigInteger.ONE)

        fun of(index: Int): MipAffine = MipAffine(mapOf(index to BigInteger.ONE))

        fun of(constant: BigInteger): MipAffine = MipAffine(constant = constant)
    }
}
