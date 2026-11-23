package io.github.gciatto.ktconstraints.core

import io.github.gciatto.ktconstraints.core.bool.BoolVar
import io.github.gciatto.ktconstraints.core.evaluation.GroundEvaluator
import io.github.gciatto.ktconstraints.core.evaluation.Valuation
import io.github.gciatto.ktconstraints.core.integer.IntLiteral
import io.github.gciatto.ktconstraints.core.integer.IntToReal
import io.github.gciatto.ktconstraints.core.integer.IntVar
import io.github.gciatto.ktconstraints.core.real.RealLiteral
import io.github.gciatto.ktconstraints.core.real.RealVar
import io.github.gciatto.ktconstraints.core.real.arithmetic.RealDifference
import io.github.gciatto.ktconstraints.core.real.arithmetic.RealNegation
import io.github.gciatto.ktconstraints.core.real.arithmetic.RealScale
import io.github.gciatto.ktconstraints.core.real.arithmetic.RealSum
import io.github.gciatto.ktconstraints.core.real.relation.RealEquality
import io.github.gciatto.ktconstraints.core.real.relation.RealLessOrEqual
import io.github.gciatto.ktconstraints.core.real.relation.RealLessThan
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.gciatto.kt.math.BigInteger

/** Linear real expressions are typed, ground-evaluated exactly, and bound through valuations. */
class RealExprSpec :
    FunSpec({
        test("real/nodes-are-typed-and-expose-operands") {
            val x = VariableFactory.empty().realVar("x")
            x.sort shouldBe Sort.Real
            RealLiteral(Rational.of(1, 2)).sort shouldBe Sort.Real
            IntToReal(IntLiteral(3)).sort shouldBe Sort.Real
            RealScale(Rational.of(2), x).children shouldBe listOf(x)
            RealLessThan(x, RealLiteral(Rational.ONE)).sort shouldBe Sort.Bool
            RealSum(x, x).variables shouldBe setOf(x)
        }

        test("real/ground-evaluation-is-exact") {
            GroundEvaluator.evaluate(RealSum(RealLiteral(Rational.of(1, 3)), RealLiteral(Rational.of(1, 6)))) shouldBe Rational.of(1, 2)
            GroundEvaluator.evaluate(RealScale(Rational.of(2, 3), RealLiteral(Rational.of(3)))) shouldBe Rational.of(2)
            GroundEvaluator.evaluate(RealDifference(RealLiteral(Rational.ZERO), RealLiteral(Rational.of(1, 7)))) shouldBe Rational.of(-1, 7)
            GroundEvaluator.evaluate(RealNegation(RealLiteral(Rational.of(5, 2)))) shouldBe Rational.of(-5, 2)
            GroundEvaluator.evaluate(IntToReal(IntLiteral("100000000000000000000"))) shouldBe
                Rational.of(BigInteger.of("100000000000000000000"))
        }

        test("real/order-and-equality-are-exact") {
            val third = RealLiteral(Rational.of(1, 3))
            val half = RealLiteral(Rational.of(1, 2))
            GroundEvaluator.evaluate(RealLessThan(third, half)) shouldBe true
            GroundEvaluator.evaluate(RealLessThan(half, half)) shouldBe false
            GroundEvaluator.evaluate(RealLessOrEqual(half, half)) shouldBe true
            GroundEvaluator.evaluate(RealEquality(RealSum(third, third), RealScale(Rational.of(2), third))) shouldBe true
        }

        test("real/valuations-bind-real-variables") {
            val x = VariableFactory.empty().realVar("x")
            val bound =
                object : Valuation {
                    override fun valueOf(variable: BoolVar): Boolean? = null

                    override fun valueOf(variable: IntVar): BigInteger? = null

                    override fun valueOf(variable: RealVar): Rational? = Rational.of(-3, 4)
                }
            GroundEvaluator.evaluate(RealScale(Rational.of(4), x), bound) shouldBe Rational.of(-3)
            val unbound =
                object : Valuation {
                    override fun valueOf(variable: BoolVar): Boolean? = null

                    override fun valueOf(variable: IntVar): BigInteger? = null
                }
            shouldThrow<IllegalArgumentException> { GroundEvaluator.evaluate(x, unbound) }
            shouldThrow<IllegalArgumentException> { GroundEvaluator.evaluate(RealSum(x, x)) }
        }
    })
