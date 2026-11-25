package io.github.gciatto.ktconstraints.core

import org.gciatto.kt.math.BigInteger
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

/**
 * Exact rational number, always in lowest terms with a positive denominator, so structural equality is numeric equality.
 * Values whose numerator and denominator fit in a `Long` are kept as machine integers, and arithmetic on operands that
 * fit in an `Int` runs without overflow on `Long`s; anything larger falls back to [BigInteger].
 */
class Rational private constructor(
    private val n: Long,
    private val d: Long,
    /** Numerator and denominator when they do not both fit in a `Long`; null otherwise, so every value has one form. */
    private val bigN: BigInteger?,
    private val bigD: BigInteger?,
) : Comparable<Rational> {
    /** Signed numerator, coprime with [denominator]. */
    val numerator: BigInteger get() = bigN ?: BigInteger.of(n)

    /** Positive denominator; one for integers. */
    val denominator: BigInteger get() = bigD ?: BigInteger.of(d)

    /** Whether this value is an integer. */
    val isInteger: Boolean get() = if (bigD == null) d == 1L else bigD == BigInteger.ONE

    /** Whether the fast path may combine this value with another one in `Long` arithmetic without overflow. */
    private val narrow: Boolean get() = bigN == null && n > Int.MIN_VALUE && n <= Int.MAX_VALUE && d <= Int.MAX_VALUE

    /** Exact sum. */
    operator fun plus(other: Rational): Rational =
        when {
            other.isZero -> this
            isZero -> other
            narrow && other.narrow ->
                if (d == other.d) reduce(n + other.n, d) else reduce(n * other.d + other.n * d, d * other.d)
            else -> of(numerator * other.denominator + other.numerator * denominator, denominator * other.denominator)
        }

    /** Exact difference. */
    operator fun minus(other: Rational): Rational = this + -other

    /** Exact product. */
    operator fun times(other: Rational): Rational =
        when {
            isZero || other.isZero -> ZERO
            narrow && other.narrow -> reduce(n * other.n, d * other.d)
            else -> of(numerator * other.numerator, denominator * other.denominator)
        }

    /** Exact additive inverse. */
    operator fun unaryMinus(): Rational =
        if (bigN == null && n != Long.MIN_VALUE) Rational(-n, d, null, null) else of(-numerator, denominator)

    /** The greatest integer not above this value. */
    fun floor(): BigInteger {
        if (bigN == null) return BigInteger.of(if (n < 0 && n % d != 0L) n / d - 1 else n / d)
        val quotient = numerator / denominator
        return if (numerator.signum < 0 && quotient * denominator != numerator) quotient - BigInteger.ONE else quotient
    }

    /** Exact quotient; fails when [other] is zero. */
    operator fun div(other: Rational): Rational {
        require(!other.isZero) { "Division by zero" }
        if (narrow && other.narrow) {
            return if (other.n < 0) reduce(-(n * other.d), -(d * other.n)) else reduce(n * other.d, d * other.n)
        }
        return of(numerator * other.denominator, denominator * other.numerator)
    }

    override fun compareTo(other: Rational): Int =
        when {
            narrow && other.narrow -> (n * other.d).compareTo(other.n * d)
            else -> (numerator * other.denominator).compareTo(other.numerator * denominator)
        }

    override fun equals(other: Any?): Boolean =
        other is Rational && n == other.n && d == other.d && bigN == other.bigN && bigD == other.bigD

    override fun hashCode(): Int = if (bigN == null) 31 * n.hashCode() + d.hashCode() else 31 * bigN.hashCode() + bigD.hashCode()

    private val isZero: Boolean get() = bigN == null && n == 0L

    /** `p/q`, or just `p` for integers; [parse] reads it back exactly. */
    override fun toString(): String = if (isInteger) numerator.toString() else "$numerator/$denominator"

    /** Exact factories. */
    companion object {
        /** Zero. */
        @JvmStatic
        val ZERO: Rational = Rational(0, 1, null, null)

        /** One. */
        @JvmStatic
        val ONE: Rational = Rational(1, 1, null, null)

        private const val LONG_BITS = 63

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
            val p = numerator / divisor
            val q = denominator / divisor
            val small = p.bitLength <= LONG_BITS && q.bitLength <= LONG_BITS
            return if (small) Rational(p.toLong(), q.toLong(), null, null) else Rational(0, 0, p, q)
        }

        /** Normalizes `p / q` for `q > 0`, both far enough from the `Long` limits that no step overflows. */
        private fun reduce(
            p: Long,
            q: Long,
        ): Rational {
            if (p == 0L) return ZERO
            var a = if (p < 0) -p else p
            var b = q
            while (b != 0L) {
                val t = a % b
                a = b
                b = t
            }
            return Rational(p / a, q / a, null, null)
        }

        /** Normalizes [numerator] / [denominator] from machine integers. */
        @JvmStatic
        @JvmOverloads
        fun of(
            numerator: Long,
            denominator: Long = 1,
        ): Rational =
            if (denominator > 0 && numerator > Long.MIN_VALUE && denominator < Long.MAX_VALUE) {
                reduce(numerator, denominator)
            } else {
                of(BigInteger.of(numerator), BigInteger.of(denominator))
            }

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
