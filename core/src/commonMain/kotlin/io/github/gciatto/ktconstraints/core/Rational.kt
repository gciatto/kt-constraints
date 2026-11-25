package io.github.gciatto.ktconstraints.core

import org.gciatto.kt.math.BigInteger
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

/**
 * Exact rational number, always in lowest terms with a positive denominator, so structural equality is numeric equality.
 * Values whose numerator and denominator fit in a `Long` are kept as machine integers, and arithmetic on them runs on
 * overflow-checked `Long`s; anything larger, or any operation that overflows, falls back to [BigInteger].
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

    /** Whether this value takes the `Long` fast path; `Long.MIN_VALUE` is left out so negation never overflows. */
    private val small: Boolean get() = bigN == null && n != Long.MIN_VALUE

    /** Exact sum. */
    operator fun plus(other: Rational): Rational {
        if (other.isZero) return this
        if (isZero) return other
        if (small && other.small) {
            // Knuth: scale by the cofactors of gcd(d, d'), keeping intermediates small.
            val g = gcd(d, other.d)
            val left = multiply(n, other.d / g)
            val right = multiply(other.n, d / g)
            val sum = add(left, right)
            val q = multiply(d / g, other.d)
            if (left != OVERFLOW && right != OVERFLOW && sum != OVERFLOW && q != OVERFLOW) return reduce(sum, q)
        }
        return of(numerator * other.denominator + other.numerator * denominator, denominator * other.denominator)
    }

    /** Exact difference. */
    operator fun minus(other: Rational): Rational = this + -other

    /** Exact product. */
    operator fun times(other: Rational): Rational {
        if (isZero || other.isZero) return ZERO
        if (small && other.small) {
            // Cross-cancelling first leaves the product in lowest terms.
            val g1 = gcd(abs(n), other.d)
            val g2 = gcd(abs(other.n), d)
            val p = multiply(n / g1, other.n / g2)
            val q = multiply(d / g2, other.d / g1)
            if (p != OVERFLOW && q != OVERFLOW) return Rational(p, q, null, null)
        }
        return of(numerator * other.numerator, denominator * other.denominator)
    }

    /** Exact additive inverse. */
    operator fun unaryMinus(): Rational = if (small) Rational(-n, d, null, null) else of(-numerator, denominator)

    /** The greatest integer not above this value. */
    fun floor(): BigInteger {
        if (bigN == null) return BigInteger.of(if (n < 0 && n % d != 0L) n / d - 1 else n / d)
        val quotient = numerator / denominator
        return if (numerator.signum < 0 && quotient * denominator != numerator) quotient - BigInteger.ONE else quotient
    }

    /** Exact quotient; fails when [other] is zero. */
    operator fun div(other: Rational): Rational {
        require(!other.isZero) { "Division by zero" }
        if (other.small) {
            return this *
                (if (other.n < 0) Rational(-other.d, -other.n, null, null) else Rational(other.d, other.n, null, null))
        }
        return of(numerator * other.denominator, denominator * other.numerator)
    }

    override fun compareTo(other: Rational): Int {
        if (small && other.small) {
            if (d == other.d) return n.compareTo(other.n)
            val left = multiply(n, other.d)
            val right = multiply(other.n, d)
            if (left != OVERFLOW && right != OVERFLOW) return left.compareTo(right)
        }
        return (numerator * other.denominator).compareTo(other.numerator * denominator)
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

        /** Marks an overflowed `Long` operation; it is also `Long.MIN_VALUE`, which the fast path never holds. */
        private const val OVERFLOW = Long.MIN_VALUE

        /** Normalizes `p / q` for `q > 0`, neither of them `Long.MIN_VALUE`. */
        private fun reduce(
            p: Long,
            q: Long,
        ): Rational {
            if (p == 0L) return ZERO
            val g = gcd(abs(p), q)
            return Rational(p / g, q / g, null, null)
        }

        private fun gcd(
            x: Long,
            y: Long,
        ): Long {
            var a = x
            var b = y
            while (b != 0L) {
                val t = a % b
                a = b
                b = t
            }
            return a
        }

        private fun abs(x: Long): Long = if (x < 0) -x else x

        /** `a·b`, or [OVERFLOW]; neither argument is `Long.MIN_VALUE`. */
        private fun multiply(
            a: Long,
            b: Long,
        ): Long {
            if (a in INT_RANGE && b in INT_RANGE) return a * b
            if (a == 0L || b == 0L) return 0
            val product = a * b
            return if (product / b != a) OVERFLOW else product
        }

        /** `a + b`, or [OVERFLOW]. */
        private fun add(
            a: Long,
            b: Long,
        ): Long {
            val sum = a + b
            return if ((a xor sum) and (b xor sum) < 0) OVERFLOW else sum
        }

        private val INT_RANGE = Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()

        /** Normalizes [numerator] / [denominator] from machine integers. */
        @JvmStatic
        @JvmOverloads
        fun of(
            numerator: Long,
            denominator: Long = 1,
        ): Rational =
            if (denominator > 0 && numerator != Long.MIN_VALUE) {
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
