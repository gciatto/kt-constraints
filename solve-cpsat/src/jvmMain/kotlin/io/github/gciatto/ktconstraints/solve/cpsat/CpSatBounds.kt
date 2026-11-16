package io.github.gciatto.ktconstraints.solve.cpsat

import org.gciatto.kt.math.BigInteger

/** Exact range of an integer subexpression and the largest absolute value CP-SAT may accumulate for it. */
internal data class CpSatBounds(
    val min: BigInteger,
    val max: BigInteger,
    val magnitude: BigInteger = min.absoluteValue.max(max.absoluteValue),
) {
    /** Whether every quantity stays within [CpSatSupport.LIMIT]. */
    val fits: Boolean
        get() = magnitude <= CpSatSupport.LIMIT && min.absoluteValue <= CpSatSupport.LIMIT && max.absoluteValue <= CpSatSupport.LIMIT
}
