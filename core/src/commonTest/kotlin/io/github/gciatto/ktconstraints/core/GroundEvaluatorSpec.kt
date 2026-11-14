package io.github.gciatto.ktconstraints.core

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
    })
