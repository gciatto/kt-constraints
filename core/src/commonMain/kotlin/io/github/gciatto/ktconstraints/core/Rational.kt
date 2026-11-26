package io.github.gciatto.ktconstraints.core

import org.gciatto.kt.math.BigInteger
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

/**
 * Exact rational number, always in lowest terms with a positive denominator, so structural equality is numeric equality.
 *
 * Values live in the smallest of three representations that holds them: `Int`, `Long` or [BigInteger] numerator and
 * denominator. Every value has exactly one representation, so equality stays numeric. Arithmetic runs in the
 * representation of its operands and moves one step up only when a result overflows: the `Int` step never touches
 * `Long`, which Kotlin/JS emulates in software, so small values stay on native numbers there.
 */
sealed class Rational : Comparable<Rational> {
    /** Signed numerator, coprime with [denominator]. */
    abstract val numerator: BigInteger

    /** Positive denominator; one for integers. */
    abstract val denominator: BigInteger

    /** Whether this value is an integer. */
    abstract val isInteger: Boolean

    /** Exact sum. */
    operator fun plus(other: Rational): Rational {
        if (other.isZero) return this
        if (isZero) return other
        if (this is Small && other is Small) Small.plus(this, other)?.let { return it }
        if (this !is Big &&
            other !is Big
        ) {
            Wide.plus(longNumerator, longDenominator, other.longNumerator, other.longDenominator)?.let { return it }
        }
        return of(numerator * other.denominator + other.numerator * denominator, denominator * other.denominator)
    }

    /** Exact difference. */
    operator fun minus(other: Rational): Rational = this + -other

    /** Exact product. */
    operator fun times(other: Rational): Rational {
        if (isZero || other.isZero) return ZERO
        if (this is Small && other is Small) Small.times(this, other)?.let { return it }
        if (this !is Big &&
            other !is Big
        ) {
            Wide.times(longNumerator, longDenominator, other.longNumerator, other.longDenominator)?.let { return it }
        }
        return of(numerator * other.numerator, denominator * other.denominator)
    }

    /** Exact additive inverse. */
    abstract operator fun unaryMinus(): Rational

    /** The greatest integer not above this value. */
    abstract fun floor(): BigInteger

    /** Exact quotient; fails when [other] is zero. */
    operator fun div(other: Rational): Rational {
        require(!other.isZero) { "Division by zero" }
        return this * other.reciprocal()
    }

    override fun compareTo(other: Rational): Int {
        if (this is Small && other is Small) Small.compare(this, other)?.let { return it }
        if (this !is Big &&
            other !is Big
        ) {
            Wide.compare(longNumerator, longDenominator, other.longNumerator, other.longDenominator)?.let { return it }
        }
        return (numerator * other.denominator).compareTo(other.numerator * denominator)
    }

    /** `p/q`, or just `p` for integers; [parse] reads it back exactly. */
    override fun toString(): String = if (isInteger) numerator.toString() else "$numerator/$denominator"

    private val isZero: Boolean get() = this is Small && n == 0

    /** Numerator and denominator as `Long`s; only read on the `Int` and `Long` representations. */
    private val longNumerator: Long get() = if (this is Small) n.toLong() else (this as Wide).n
    private val longDenominator: Long get() = if (this is Small) d.toLong() else (this as Wide).d

    private fun reciprocal(): Rational =
        when (this) {
            is Small -> if (n < 0) Small(-d, -n) else Small(d, n)
            is Wide -> if (n < 0) Wide(-d, -n) else Wide(d, n)
            is Big -> of(denominator, numerator)
        }

    /** `n/d` with both within `Int`, and `n` above `Int.MIN_VALUE` so negation stays within `Int`. */
    private class Small(
        val n: Int,
        val d: Int,
    ) : Rational() {
        override val numerator: BigInteger get() = BigInteger.of(n.toLong())
        override val denominator: BigInteger get() = BigInteger.of(d.toLong())
        override val isInteger: Boolean get() = d == 1

        override fun unaryMinus(): Rational = Small(-n, d)

        override fun floor(): BigInteger = BigInteger.of((if (n < 0 && n % d != 0) n / d - 1 else n / d).toLong())

        override fun equals(other: Any?): Boolean = other is Small && n == other.n && d == other.d

        override fun hashCode(): Int = HASH * n + d

        companion object {
            /** Knuth: scale by the cofactors of gcd(d, d'), keeping intermediates small. Null on overflow. */
            fun plus(
                a: Small,
                b: Small,
            ): Rational? {
                val g = gcd(a.d, b.d)
                val left = multiply(a.n, b.d / g)
                val right = multiply(b.n, a.d / g)
                val q = multiply(a.d / g, b.d)
                if (left == OVERFLOW || right == OVERFLOW || q == OVERFLOW) return null
                val sum = add(left, right)
                return if (sum == OVERFLOW) null else reduce(sum, q)
            }

            /** Cross-cancelling first leaves the product in lowest terms. Null on overflow. */
            fun times(
                a: Small,
                b: Small,
            ): Rational? {
                val g1 = gcd(abs(a.n), b.d)
                val g2 = gcd(abs(b.n), a.d)
                val p = multiply(a.n / g1, b.n / g2)
                val q = multiply(a.d / g2, b.d / g1)
                return if (p == OVERFLOW || q == OVERFLOW) null else Small(p, q)
            }

            /** Null on overflow. */
            fun compare(
                a: Small,
                b: Small,
            ): Int? {
                if (a.d == b.d) return a.n.compareTo(b.n)
                val left = multiply(a.n, b.d)
                val right = multiply(b.n, a.d)
                return if (left == OVERFLOW || right == OVERFLOW) null else left.compareTo(right)
            }

            /** Marks an overflowed operation; it is also `Int.MIN_VALUE`, which no `Int` representation holds. */
            private const val OVERFLOW = Int.MIN_VALUE

            private fun reduce(
                p: Int,
                q: Int,
            ): Rational {
                if (p == 0) return ZERO
                val g = gcd(abs(p), q)
                return Small(p / g, q / g)
            }

            private fun gcd(
                x: Int,
                y: Int,
            ): Int {
                var a = x
                var b = y
                while (b != 0) {
                    val t = a % b
                    a = b
                    b = t
                }
                return a
            }

            private fun abs(x: Int): Int = if (x < 0) -x else x

            /** `a·b`, or [OVERFLOW]: a double holds the product exactly whenever it is within `Int` range. */
            private fun multiply(
                a: Int,
                b: Int,
            ): Int {
                val product = a.toDouble() * b.toDouble()
                return if (product > Int.MIN_VALUE && product <= Int.MAX_VALUE) product.toInt() else OVERFLOW
            }

            private fun add(
                a: Int,
                b: Int,
            ): Int {
                val sum = a + b
                return if ((a xor sum) and (b xor sum) < 0 || sum == OVERFLOW) OVERFLOW else sum
            }
        }
    }

    /** `n/d` with both within `Long` but not both within `Int`, and `n` above `Long.MIN_VALUE`. */
    private class Wide(
        val n: Long,
        val d: Long,
    ) : Rational() {
        override val numerator: BigInteger get() = BigInteger.of(n)
        override val denominator: BigInteger get() = BigInteger.of(d)
        override val isInteger: Boolean get() = d == 1L

        override fun unaryMinus(): Rational = Wide(-n, d)

        override fun floor(): BigInteger = BigInteger.of(if (n < 0 && n % d != 0L) n / d - 1 else n / d)

        override fun equals(other: Any?): Boolean = other is Wide && n == other.n && d == other.d

        override fun hashCode(): Int = HASH * n.hashCode() + d.hashCode()

        companion object {
            /** Null on overflow. */
            fun plus(
                an: Long,
                ad: Long,
                bn: Long,
                bd: Long,
            ): Rational? {
                val g = gcd(ad, bd)
                val left = multiply(an, bd / g)
                val right = multiply(bn, ad / g)
                val q = multiply(ad / g, bd)
                if (left == OVERFLOW || right == OVERFLOW || q == OVERFLOW) return null
                val sum = add(left, right)
                return if (sum == OVERFLOW) null else reduce(sum, q)
            }

            /** Null on overflow. */
            fun times(
                an: Long,
                ad: Long,
                bn: Long,
                bd: Long,
            ): Rational? {
                val g1 = gcd(abs(an), bd)
                val g2 = gcd(abs(bn), ad)
                val p = multiply(an / g1, bn / g2)
                val q = multiply(ad / g2, bd / g1)
                return if (p == OVERFLOW || q == OVERFLOW) null else of(p, q)
            }

            /** Null on overflow. */
            fun compare(
                an: Long,
                ad: Long,
                bn: Long,
                bd: Long,
            ): Int? {
                if (ad == bd) return an.compareTo(bn)
                val left = multiply(an, bd)
                val right = multiply(bn, ad)
                return if (left == OVERFLOW || right == OVERFLOW) null else left.compareTo(right)
            }

            /** Marks an overflowed operation; it is also `Long.MIN_VALUE`, which no `Long` representation holds. */
            private const val OVERFLOW = Long.MIN_VALUE

            /** `p/q` in lowest terms, for `q > 0` and neither of them `Long.MIN_VALUE`. */
            fun reduce(
                p: Long,
                q: Long,
            ): Rational {
                if (p == 0L) return ZERO
                val g = gcd(abs(p), q)
                return of(p / g, q / g)
            }

            /** The representation of the already reduced `p/q`, for `q > 0`: `Int` when both fit in one. */
            fun of(
                p: Long,
                q: Long,
            ): Rational = if (p > Int.MIN_VALUE && p <= Int.MAX_VALUE && q <= Int.MAX_VALUE) Small(p.toInt(), q.toInt()) else Wide(p, q)

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

            private fun multiply(
                a: Long,
                b: Long,
            ): Long {
                if (a == 0L || b == 0L) return 0
                val product = a * b
                return if (product / b != a || product == OVERFLOW) OVERFLOW else product
            }

            private fun add(
                a: Long,
                b: Long,
            ): Long {
                val sum = a + b
                return if ((a xor sum) and (b xor sum) < 0) OVERFLOW else sum
            }
        }
    }

    /** `n/d` when they do not both fit in a `Long`. */
    private class Big(
        override val numerator: BigInteger,
        override val denominator: BigInteger,
    ) : Rational() {
        override val isInteger: Boolean get() = denominator == BigInteger.ONE

        override fun unaryMinus(): Rational = of(-numerator, denominator)

        override fun floor(): BigInteger {
            val quotient = numerator / denominator
            return if (numerator.signum < 0 && quotient * denominator != numerator) quotient - BigInteger.ONE else quotient
        }

        override fun equals(other: Any?): Boolean = other is Big && numerator == other.numerator && denominator == other.denominator

        override fun hashCode(): Int = HASH * numerator.hashCode() + denominator.hashCode()
    }

    /** Exact factories. */
    companion object {
        /** Zero. */
        @JvmStatic
        val ZERO: Rational = Small(0, 1)

        /** One. */
        @JvmStatic
        val ONE: Rational = Small(1, 1)

        private const val HASH = 31
        private const val LONG_BITS = 63
        private val LONG_MIN = BigInteger.of(Long.MIN_VALUE)
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
            // `Long.MIN_VALUE` fits in 63 bits too, but no `Long` representation may hold it.
            val fits = p.bitLength <= LONG_BITS && q.bitLength <= LONG_BITS && p != LONG_MIN
            return if (fits) Wide.of(p.toLong(), q.toLong()) else Big(p, q)
        }

        /** Normalizes [numerator] / [denominator] from machine integers. */
        @JvmStatic
        @JvmOverloads
        fun of(
            numerator: Long,
            denominator: Long = 1,
        ): Rational =
            if (denominator > 0 && numerator != Long.MIN_VALUE) {
                Wide.reduce(numerator, denominator)
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
