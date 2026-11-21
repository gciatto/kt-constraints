package io.github.gciatto.ktconstraints.solve.kotlin

import org.gciatto.kt.math.BigInteger

/** Searches integer objective bounds by bisection; minimization is maximization of the negated score. */
internal class OptimizationState(
    private val maximize: Boolean,
    initialUpperScore: BigInteger,
) {
    private var upper = initialUpperScore
    private var best: BigInteger? = null

    val proven: Boolean get() = best?.let { it >= upper } == true
    val bestBound: BigInteger get() = if (maximize) upper else -upper

    fun target(): BigInteger? = best?.let { it + (upper - it + BigInteger.ONE) / BigInteger.TWO }

    fun refuted(target: BigInteger) {
        upper = target - BigInteger.ONE
    }

    fun improved(value: BigInteger) {
        val score = if (maximize) value else -value
        check(best == null || score > requireNotNull(best)) { "Incumbent did not improve" }
        best = score
    }
}
