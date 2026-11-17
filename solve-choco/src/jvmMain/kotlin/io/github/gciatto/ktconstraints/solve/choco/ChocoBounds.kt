package io.github.gciatto.ktconstraints.solve.choco

import org.gciatto.kt.math.BigInteger

/** Exact range of an integer subexpression and the largest absolute value Choco may accumulate for it. */
internal data class ChocoBounds(
    val min: BigInteger,
    val max: BigInteger,
    val magnitude: BigInteger = min.absoluteValue.max(max.absoluteValue),
) {
    /** Whether every quantity stays within [ChocoSupport.LIMIT]. */
    val fits: Boolean
        get() = magnitude <= ChocoSupport.LIMIT && min.absoluteValue <= ChocoSupport.LIMIT && max.absoluteValue <= ChocoSupport.LIMIT
}
