package io.github.gciatto.ktconstraints.solve.kotlin.arithmetic

import io.github.gciatto.ktconstraints.core.Rational
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/** Caller-owned collections cannot change an arithmetic problem after construction. */
class LinearInequalitySpec :
    FunSpec({
        test("linear-row/copies-coefficients-and-drops-zero-terms") {
            val source = mutableMapOf(0 to Rational.ONE, 1 to Rational.ZERO)
            val row = LinearInequality(source, Rational.of(4), strict = true)
            source[0] = Rational.of(9)
            source[2] = Rational.ONE
            row.coefficients shouldBe mapOf(0 to Rational.ONE)
            row.upperBound shouldBe Rational.of(4)
            row.strict shouldBe true
        }
    })
