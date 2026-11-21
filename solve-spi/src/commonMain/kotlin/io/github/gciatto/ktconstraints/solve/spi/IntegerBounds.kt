package io.github.gciatto.ktconstraints.solve.spi

import org.gciatto.kt.math.BigInteger

/** Exact range of an integer subexpression and the largest absolute value a backend may accumulate for it. */
data class IntegerBounds(
    /** Least value. */
    val min: BigInteger,
    /** Greatest value. */
    val max: BigInteger,
    /** Largest absolute intermediate value. */
    val magnitude: BigInteger = min.absoluteValue.max(max.absoluteValue),
) {
    /** Whether every quantity stays within [limit]. */
    fun fits(limit: BigInteger): Boolean = magnitude <= limit && min.absoluteValue <= limit && max.absoluteValue <= limit
}
