package io.github.gciatto.ktconstraints.core.domain
import org.gciatto.kt.math.BigInteger

/** Non-empty inclusive interval of exact integers. */
data class IntInterval(
    /** Inclusive lower bound. */
    val min: BigInteger,
    /** Inclusive upper bound. */
    val max: BigInteger,
) {
    init {
        require(min <= max) { "Expected min <= max, got $min > $max" }
    }

    override fun toString(): String = if (min == max) min.toString() else "$min..$max"
}
