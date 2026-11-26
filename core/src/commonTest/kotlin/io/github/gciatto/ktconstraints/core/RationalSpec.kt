package io.github.gciatto.ktconstraints.core

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withTests
import io.kotest.matchers.shouldBe
import org.gciatto.kt.math.BigInteger
import kotlin.random.Random

/** Exact rationals are normalized, so structural equality is numeric equality. */
class RationalSpec :
    FunSpec({
        test("rational/normalizes-to-lowest-terms-with-positive-denominator") {
            Rational.of(2, 4) shouldBe Rational.of(1, 2)
            Rational.of(2, 4).hashCode() shouldBe Rational.of(1, 2).hashCode()
            Rational.of(1, -3).numerator shouldBe BigInteger.of(-1)
            Rational.of(1, -3).denominator shouldBe BigInteger.of(3)
            Rational.of(0, -5) shouldBe Rational.ZERO
            Rational.ZERO.denominator shouldBe BigInteger.ONE
        }

        test("rational/zero-denominator-is-rejected") {
            shouldThrow<IllegalArgumentException> { Rational.of(1, 0) }
        }

        test("rational/arithmetic-is-exact") {
            Rational.of(1, 3) + Rational.of(1, 6) shouldBe Rational.of(1, 2)
            Rational.of(1, 3) * Rational.of(3) shouldBe Rational.ONE
            Rational.of(1, 3) - Rational.of(1, 2) shouldBe Rational.of(-1, 6)
            -Rational.of(2, 7) shouldBe Rational.of(-2, 7)
            val huge = BigInteger.of("1000000000000000000000000000001")
            Rational.of(huge, BigInteger.of(3)) * Rational.of(3) shouldBe Rational.of(huge)
            Rational.of(1, 3) / Rational.of(-2, 9) shouldBe Rational.of(-3, 2)
            shouldThrow<IllegalArgumentException> { Rational.ONE / Rational.ZERO }
            Rational.of(7, 2).floor() shouldBe BigInteger.of(3)
            Rational.of(-7, 2).floor() shouldBe BigInteger.of(-4)
            Rational.of(-4).floor() shouldBe BigInteger.of(-4)
        }

        withTests(
            nameFn = { "rational/order/${it.first}<${it.second}" },
            listOf(Rational.of(-1, 2) to Rational.ZERO, Rational.of(1, 3) to Rational.of(1, 2), Rational.of(2) to Rational.of(7, 3)),
        ) { (smaller, larger) ->
            (smaller < larger) shouldBe true
            (larger < smaller) shouldBe false
            smaller.compareTo(smaller) shouldBe 0
        }

        test("rational/text-round-trips-exactly") {
            Rational.parse("-2/4") shouldBe Rational.of(-1, 2)
            Rational.parse("-2/4").toString() shouldBe "-1/2"
            Rational.parse("7").toString() shouldBe "7"
            Rational.parse("123456789012345678901234567890/11").toString() shouldBe "123456789012345678901234567890/11"
            Rational.parse("1.5") shouldBe Rational.of(3, 2)
            Rational.parse("-0.125") shouldBe Rational.of(-1, 8)
            Rational.parse("0.1") shouldBe Rational.of(1, 10)
            Rational.parse("2.50") shouldBe Rational.of(5, 2)
            listOf("1.", ".5", "1e3", "1.5/2", "+1", "").forEach { text ->
                shouldThrow<IllegalArgumentException> { Rational.parse(text) }
            }
            shouldThrow<IllegalArgumentException> { Rational.parse("1/0") }
        }

        test("rational/integers-are-recognized") {
            Rational.of(6, 3).isInteger shouldBe true
            Rational.of(1, 3).isInteger shouldBe false
        }

        test("rational/machine-and-big-arithmetic-agree-across-the-limits") {
            val random = Random(0x2A710)
            val edges = listOf(0L, 1L, -1L, Int.MAX_VALUE.toLong(), Int.MIN_VALUE.toLong(), Long.MAX_VALUE, Long.MIN_VALUE + 1)

            fun component(): Long =
                when (random.nextInt(4)) {
                    0 -> edges.random(random)
                    1 -> random.nextLong(-1000, 1000)
                    2 -> random.nextLong(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong())
                    else -> random.nextLong()
                }

            // Some values exceed Long, so every pair of the Int, Long and BigInteger representations meets.
            fun big(value: Long): BigInteger =
                if (random.nextInt(5) ==
                    0
                ) {
                    BigInteger.of(value) * BigInteger.of(Long.MAX_VALUE)
                } else {
                    BigInteger.of(value)
                }

            fun value(): Rational = Rational.of(big(component()), big(component().let { if (it == 0L) 7 else it }))

            fun big(r: Rational) = r.numerator to r.denominator
            repeat(4000) {
                val a = value()
                val b = value()
                val (an, ad) = big(a)
                val (bn, bd) = big(b)
                a + b shouldBe Rational.of(an * bd + bn * ad, ad * bd)
                a * b shouldBe Rational.of(an * bn, ad * bd)
                a - b shouldBe Rational.of(an * bd - bn * ad, ad * bd)
                if (b != Rational.ZERO) a / b shouldBe Rational.of(an * bd, ad * bn)
                a.compareTo(b) shouldBe (an * bd).compareTo(bn * ad)
                (a == Rational.of(an, ad)) shouldBe true
                a.hashCode() shouldBe Rational.of(an, ad).hashCode()
                a.floor() shouldBe
                    Rational.of(an, ad).let { if (an.signum < 0 && (an % ad).signum != 0) an / ad - BigInteger.ONE else an / ad }
            }
        }
    })
