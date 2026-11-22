package io.github.gciatto.ktconstraints.solve.kotlin.compiler

import org.gciatto.kt.math.BigInteger

/**
 * `constant + sum(coefficient * encoding)`, kept symbolic so comparisons become native linear constraints;
 * consumers needing bits materialise it as an adder circuit. Identity equality keys that materialisation.
 */
internal class IntegerLinear(
    val parts: List<Pair<IntegerEncoding, BigInteger>>,
    val constant: BigInteger,
) {
    val min: BigInteger = parts.fold(constant) { sum, (encoding, coefficient) -> sum + extreme(encoding, coefficient, lower = true) }
    val max: BigInteger = parts.fold(constant) { sum, (encoding, coefficient) -> sum + extreme(encoding, coefficient, lower = false) }

    operator fun plus(other: IntegerLinear): IntegerLinear = IntegerLinear(parts + other.parts, constant + other.constant)

    fun scale(factor: BigInteger): IntegerLinear =
        IntegerLinear(
            parts.map { (encoding, coefficient) -> encoding to coefficient * factor },
            constant * factor,
        )

    private fun extreme(
        encoding: IntegerEncoding,
        coefficient: BigInteger,
        lower: Boolean,
    ): BigInteger = coefficient * if ((coefficient.signum >= 0) == lower) encoding.min else encoding.max

    companion object {
        fun of(encoding: IntegerEncoding): IntegerLinear = IntegerLinear(listOf(encoding to BigInteger.ONE), BigInteger.ZERO)
    }
}
