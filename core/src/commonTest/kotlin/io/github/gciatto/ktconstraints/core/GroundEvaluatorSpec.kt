package io.github.gciatto.ktconstraints.core

import io.github.gciatto.ktconstraints.core.bool.BoolAnd
import io.github.gciatto.ktconstraints.core.bool.BoolEquality
import io.github.gciatto.ktconstraints.core.bool.BoolImplies
import io.github.gciatto.ktconstraints.core.bool.BoolLiteral
import io.github.gciatto.ktconstraints.core.bool.BoolNot
import io.github.gciatto.ktconstraints.core.bool.BoolOr
import io.github.gciatto.ktconstraints.core.bool.BoolVar
import io.github.gciatto.ktconstraints.core.domain.IntDomain
import io.github.gciatto.ktconstraints.core.evaluation.GroundEvaluator
import io.github.gciatto.ktconstraints.core.evaluation.UndefinedExpressionException
import io.github.gciatto.ktconstraints.core.evaluation.Valuation
import io.github.gciatto.ktconstraints.core.global.extensional.Element
import io.github.gciatto.ktconstraints.core.integer.IntExpr
import io.github.gciatto.ktconstraints.core.integer.IntLiteral
import io.github.gciatto.ktconstraints.core.integer.IntVar
import io.github.gciatto.ktconstraints.core.integer.arithmetic.IntDifference
import io.github.gciatto.ktconstraints.core.integer.arithmetic.IntNegation
import io.github.gciatto.ktconstraints.core.integer.arithmetic.IntProduct
import io.github.gciatto.ktconstraints.core.integer.arithmetic.IntSum
import io.github.gciatto.ktconstraints.core.integer.relation.IntEquality
import io.github.gciatto.ktconstraints.core.integer.relation.IntLessOrEqual
import io.github.gciatto.ktconstraints.core.integer.relation.IntLessThan
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withTests
import io.kotest.matchers.shouldBe
import org.gciatto.kt.math.BigInteger

/** Pure evaluation is exact and rejects non-ground or unsupported expressions. */
class GroundEvaluatorSpec :
    FunSpec({
        test("integer/arithmetic-does-not-overflow") {
            val large = IntLiteral("9223372036854775808")
            GroundEvaluator.evaluate(IntSum(large, large)).toString() shouldBe "18446744073709551616"
            GroundEvaluator.evaluate(IntDifference(IntLiteral(-3), large)).toString() shouldBe "-9223372036854775811"
            GroundEvaluator.evaluate(IntProduct(large, IntLiteral(3))).toString() shouldBe "27670116110564327424"
            GroundEvaluator.evaluate(IntNegation(large)).toString() shouldBe "-9223372036854775808"
        }

        withTests(
            nameFn = { "boolean/truth-table/$it" },
            listOf(false to false, false to true, true to false, true to true),
        ) { (left, right) ->
            val a = BoolLiteral(left)
            val b = BoolLiteral(right)
            GroundEvaluator.evaluate(BoolAnd(a, b)) shouldBe (left && right)
            GroundEvaluator.evaluate(BoolOr(a, b)) shouldBe (left || right)
            GroundEvaluator.evaluate(BoolImplies(a, b)) shouldBe (!left || right)
            GroundEvaluator.evaluate(BoolNot(a)) shouldBe !left
            GroundEvaluator.evaluate(BoolEquality(a, b)) shouldBe (left == right)
        }

        withTests(nameFn = { "integer/order/$it" }, listOf(-2L to 3L, 3L to -2L, 3L to 3L)) { (left, right) ->
            val a = IntLiteral(left)
            val b = IntLiteral(right)
            GroundEvaluator.evaluate(IntEquality(a, b)) shouldBe (left == right)
            GroundEvaluator.evaluate(IntLessThan(a, b)) shouldBe (left < right)
            GroundEvaluator.evaluate(IntLessOrEqual(a, b)) shouldBe (left <= right)
        }

        test("evaluation/rejects-integer-variables") {
            val x = VariableFactory.empty().intVar("x", IntDomain.of(1, 2))
            shouldThrow<IllegalArgumentException> { GroundEvaluator.evaluate(IntSum(x, IntLiteral(1))) }
        }

        test("evaluation/does-not-hide-variables-behind-short-circuit") {
            val x = VariableFactory.empty().boolVar("x")
            shouldThrow<IllegalArgumentException> { GroundEvaluator.evaluate(BoolAnd(BoolLiteral(false), x)) }
        }

        test("evaluation/rejects-unknown-extension-nodes") {
            val extension =
                object : IntExpr {
                    override val variables: Set<Variable> = emptySet()
                }
            shouldThrow<IllegalArgumentException> { GroundEvaluator.evaluate(extension) }
        }

        test("integer/nested-expression-is-exact") {
            val expression = IntSum(IntProduct(IntLiteral(-4), IntLiteral(3)), IntNegation(IntLiteral(-2)))
            GroundEvaluator.evaluate(expression) shouldBe BigInteger.of(-10)
        }

        test("evaluation/binds-typed-variables-without-rewriting-the-expression") {
            val factory = VariableFactory.empty()
            val x = factory.intVar("x", IntDomain.of(0, 3))
            val flag = factory.boolVar("flag")
            val values =
                object : Valuation {
                    override fun valueOf(variable: IntVar): BigInteger? = if (variable == x) BigInteger.TWO else null

                    override fun valueOf(variable: BoolVar): Boolean? = if (variable == flag) true else null
                }
            GroundEvaluator.evaluate(IntSum(x, IntLiteral(1)), values) shouldBe BigInteger.of(3)
            GroundEvaluator.evaluate(BoolAnd(flag, IntEquality(x, IntLiteral(2))), values) shouldBe true
        }

        test("evaluation/rejects-missing-and-out-of-domain-bindings") {
            val x = VariableFactory.empty().intVar("x", IntDomain.values(1, 3))
            val missing =
                object : Valuation {
                    override fun valueOf(variable: IntVar): BigInteger? = null

                    override fun valueOf(variable: BoolVar): Boolean? = null
                }
            val invalid =
                object : Valuation {
                    override fun valueOf(variable: IntVar): BigInteger? = BigInteger.TWO

                    override fun valueOf(variable: BoolVar): Boolean? = null
                }
            shouldThrow<IllegalArgumentException> { GroundEvaluator.evaluate(x, missing) }
            shouldThrow<IllegalArgumentException> { GroundEvaluator.evaluate(x, invalid) }
        }

        test("element/undefined-indices-have-a-distinct-error") {
            shouldThrow<UndefinedExpressionException> {
                GroundEvaluator.evaluate(Element(IntLiteral(-1), listOf(IntLiteral(2))))
            }
        }
    })
