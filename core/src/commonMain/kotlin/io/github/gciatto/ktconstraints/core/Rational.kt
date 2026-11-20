package io.github.gciatto.ktconstraints.core

import org.gciatto.kt.math.BigInteger
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

/** Exact rational number, always in lowest terms with a positive denominator, so structural equality is numeric equality. */
class Rational private constructor(
    /** Signed numerator, coprime with [denominator]. */
    val numerator: BigInteger,
    /** Positive denominator; one for integers. */
    val denominator: BigInteger,
) : Comparable<Rational> {
    /** Whether this value is an integer. */
    val isInteger: Boolean get() = denominator == BigInteger.ONE

    /** Exact sum. */
    operator fun plus(other: Rational): Rational =
        of(
            numerator * other.denominator + other.numerator * denominator,
            denominator * other.denominator,
        )

    /** Exact difference. */
    operator fun minus(other: Rational): Rational = this + -other

    /** Exact product. */
    operator fun times(other: Rational): Rational = of(numerator * other.numerator, denominator * other.denominator)

    /** Exact additive inverse. */
    operator fun unaryMinus(): Rational = Rational(-numerator, denominator)

    override fun compareTo(other: Rational): Int = (numerator * other.denominator).compareTo(other.numerator * denominator)

    override fun equals(other: Any?): Boolean = other is Rational && numerator == other.numerator && denominator == other.denominator

    override fun hashCode(): Int = 31 * numerator.hashCode() + denominator.hashCode()

    /** `p/q`, or just `p` for integers; [parse] reads it back exactly. */
    override fun toString(): String = if (isInteger) numerator.toString() else "$numerator/$denominator"

    /** Exact factories. */
    companion object {
        /** Zero. */
        @JvmStatic
        val ZERO: Rational = Rational(BigInteger.ZERO, BigInteger.ONE)

        /** One. */
        @JvmStatic
        val ONE: Rational = Rational(BigInteger.ONE, BigInteger.ONE)

        private val TEXT = Regex("-?[0-9]+(/[0-9]+|\\.[0-9]+)?")

        /** Normalizes [numerator] / [denominator]; a zero denominator is rejected. */
        @JvmStatic
        @JvmOverloads
        fun of(
            numerator: BigInteger,
            denominator: BigInteger = BigInteger.ONE,
        ): Rational {
            require(denominator.signum != 0) { "Rational denominator must not be zero" }
            val divisor = numerator.gcd(denominator).let { if (denominator.signum < 0) -it else it }
            return Rational(numerator / divisor, denominator / divisor)
        }

        /** Normalizes [numerator] / [denominator] from machine integers. */
        @JvmStatic
        @JvmOverloads
        fun of(
            numerator: Long,
            denominator: Long = 1,
        ): Rational = of(BigInteger.of(numerator), BigInteger.of(denominator))

        /** Parses `p`, `p/q` or a decimal `p.d` exactly; exponents and other notations are rejected. */
        @JvmStatic
        fun parse(text: String): Rational {
            require(TEXT.matches(text)) { "Not an exact rational: $text" }
            if ('.' in text) {
                val (whole, fraction) = text.split('.')
                return of(BigInteger.of(whole + fraction), BigInteger.TEN.pow(fraction.length))
            }
            val parts = text.split('/')
            return of(BigInteger.of(parts[0]), if (parts.size == 2) BigInteger.of(parts[1]) else BigInteger.ONE)
        }
    }
}
