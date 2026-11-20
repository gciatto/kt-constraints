package io.github.gciatto.ktconstraints.dsl

import io.github.gciatto.ktconstraints.core.BoolNot
import io.github.gciatto.ktconstraints.core.IntToReal
import io.github.gciatto.ktconstraints.core.Rational
import io.github.gciatto.ktconstraints.core.RealDifference
import io.github.gciatto.ktconstraints.core.RealEquality
import io.github.gciatto.ktconstraints.core.RealLessOrEqual
import io.github.gciatto.ktconstraints.core.RealLessThan
import io.github.gciatto.ktconstraints.core.RealLiteral
import io.github.gciatto.ktconstraints.core.RealNegation
import io.github.gciatto.ktconstraints.core.RealScale
import io.github.gciatto.ktconstraints.core.RealSum
import io.github.gciatto.ktconstraints.model.ConstraintScope
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/** Real operators build the exact linear real nodes; machine integers become exact rational literals. */
class RealDslSpec :
    FunSpec({
        test("real/arithmetic-builds-linear-nodes") {
            val scope = ConstraintScope.empty()
            val x = scope.realVar("x")
            val y = scope.realVar("y")
            val half = Rational.of(1, 2)
            (x + y) shouldBe RealSum(x, y)
            (x + 1) shouldBe RealSum(x, RealLiteral(Rational.ONE))
            (x + 1L) shouldBe RealSum(x, RealLiteral(Rational.ONE))
            (x + half) shouldBe RealSum(x, RealLiteral(half))
            (x - y) shouldBe RealDifference(x, y)
            (x - 2) shouldBe RealDifference(x, RealLiteral(Rational.of(2)))
            (x - half) shouldBe RealDifference(x, RealLiteral(half))
            (x * 3) shouldBe RealScale(Rational.of(3), x)
            (x * 3L) shouldBe RealScale(Rational.of(3), x)
            (x * half) shouldBe RealScale(half, x)
            (-x) shouldBe RealNegation(x)
        }
        test("real/relations-build-equality-and-order-nodes") {
            val scope = ConstraintScope.empty()
            val x = scope.realVar("x")
            val y = scope.realVar("y")
            val two = RealLiteral(Rational.of(2))
            (x eq y) shouldBe RealEquality(x, y)
            (x eq 2) shouldBe RealEquality(x, two)
            (x neq 2L) shouldBe BoolNot(RealEquality(x, two))
            (x lt Rational.of(2)) shouldBe RealLessThan(x, two)
            (x le 2) shouldBe RealLessOrEqual(x, two)
            (x gt 2) shouldBe RealLessThan(two, x)
            (x ge y) shouldBe RealLessOrEqual(y, x)
        }
        test("real/integers-embed-explicitly") {
            val scope = ConstraintScope.empty()
            val n = scope.intVar("n", 0..3)
            n.toReal() shouldBe IntToReal(n)
        }
    })
