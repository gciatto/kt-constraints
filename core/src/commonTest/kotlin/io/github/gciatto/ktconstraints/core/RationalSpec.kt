package io.github.gciatto.ktconstraints.core

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withTests
import io.kotest.matchers.shouldBe
import org.gciatto.kt.math.BigInteger

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
    })
