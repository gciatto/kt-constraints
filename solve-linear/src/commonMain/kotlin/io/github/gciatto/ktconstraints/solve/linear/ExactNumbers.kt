package io.github.gciatto.ktconstraints.solve.linear

import io.github.gciatto.ktconstraints.core.Rational
import org.gciatto.kt.math.BigInteger

private const val MANTISSA_BITS = 52
private const val EXPONENT_MASK = 0x7FF
private const val MANTISSA_MASK = (1L shl MANTISSA_BITS) - 1
private const val EXPONENT_BIAS = 1075
private const val SUBNORMAL_EXPONENT = -1074

/** The exact rational value of a finite [value]: every double is a dyadic fraction `m·2^e`. */
fun exactRational(value: Double): Rational {
    require(value.isFinite()) { "Not a finite number: $value" }
    val bits = value.toRawBits()
    val exponentBits = ((bits shr MANTISSA_BITS) and EXPONENT_MASK.toLong()).toInt()
    val fraction = bits and MANTISSA_MASK
    val (mantissa, exponent) =
        if (exponentBits == 0) fraction to SUBNORMAL_EXPONENT else (fraction or (1L shl MANTISSA_BITS)) to exponentBits - EXPONENT_BIAS
    val magnitude = BigInteger.of(if (bits < 0) -mantissa else mantissa)
    return if (exponent >=
        0
    ) {
        Rational.of(magnitude * BigInteger.TWO.pow(exponent))
    } else {
        Rational.of(magnitude, BigInteger.TWO.pow(-exponent))
    }
}

/** The greatest integer not above [value]. */
internal fun floor(value: Rational): BigInteger {
    val quotient = value.numerator / value.denominator
    return if (value.numerator.signum < 0 && quotient * value.denominator != value.numerator) quotient - BigInteger.ONE else quotient
}

/** The integer nearest to [value], rounding halves up. */
internal fun nearest(value: Rational): BigInteger = floor(value + Rational.of(1, 2))

/** The absolute value of [value]. */
internal fun abs(value: Rational): Rational = if (value < Rational.ZERO) -value else value

/**
 * The nearest double to [value] as far as both parts convert: for floating-point engines only, whose answers are
 * re-checked exactly. Fails when the parts overflow, since such a coefficient cannot be passed on meaningfully.
 */
fun approximate(value: Rational): Double {
    val approximation = value.numerator.toDouble() / value.denominator.toDouble()
    require(approximation.isFinite()) { "$value has no finite double approximation" }
    return approximation
}
