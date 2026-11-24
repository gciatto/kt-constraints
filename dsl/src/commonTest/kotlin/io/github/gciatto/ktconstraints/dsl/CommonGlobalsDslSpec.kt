package io.github.gciatto.ktconstraints.dsl

import io.github.gciatto.ktconstraints.core.bool.BoolLiteral
import io.github.gciatto.ktconstraints.core.evaluation.GroundEvaluator
import io.github.gciatto.ktconstraints.core.global.graph.Circuit
import io.github.gciatto.ktconstraints.core.global.graph.Inverse
import io.github.gciatto.ktconstraints.core.global.scheduling.Cumulative
import io.github.gciatto.ktconstraints.core.integer.IntExpr
import io.github.gciatto.ktconstraints.core.integer.IntLiteral
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.gciatto.kt.math.BigInteger

/** Counting, ordering and structural helpers: exact semantics over constants. */
class CommonGlobalsDslSpec :
    FunSpec({
        fun ints(vararg values: Long): List<IntExpr> = values.map { IntLiteral(it) }

        fun big(vararg values: Long): List<BigInteger> = values.map { BigInteger.of(it) }

        fun eval(expression: Any) =
            when (expression) {
                is io.github.gciatto.ktconstraints.core.bool.BoolExpr -> GroundEvaluator.evaluate(expression)
                is IntExpr -> GroundEvaluator.evaluate(expression).toString()
                else -> error("Not an expression")
            }

        test("dsl/counting") {
            eval(BoolLiteral(true).asIntExpr()) shouldBe "1"
            eval(count(IntLiteral(2), *ints(2, 1, 2).toTypedArray())) shouldBe "2"
            eval(count(IntLiteral(2))) shouldBe "0"
            eval(among(big(1, 3), *ints(1, 2, 3, 3).toTypedArray())) shouldBe "3"
            eval(globalCardinality(ints(1, 2, 1), big(1, 2, 5), ints(2, 1, 0))) shouldBe true
            eval(globalCardinality(ints(1, 2, 1), big(1), ints(1))) shouldBe false
            shouldThrow<IllegalArgumentException> { globalCardinality(ints(1), big(1, 2), ints(1)) }
        }

        test("dsl/ordering") {
            eval(increasing(*ints(1, 1, 3).toTypedArray())) shouldBe true
            eval(increasing(*ints(2, 1).toTypedArray())) shouldBe false
            eval(decreasing(*ints(3, 3, 1).toTypedArray())) shouldBe true
            eval(increasing()) shouldBe true
            eval(lexLessOrEqual(ints(1, 2), ints(1, 2))) shouldBe true
            eval(lexLess(ints(1, 2), ints(1, 2))) shouldBe false
            eval(lexLess(ints(1, 2, 9), ints(1, 3, 0))) shouldBe true
            eval(lexLessOrEqual(ints(2), ints(1))) shouldBe false
            eval(lexLess(emptyList(), emptyList())) shouldBe false
            shouldThrow<IllegalArgumentException> { lexLess(ints(1), emptyList()) }
        }

        test("dsl/structural") {
            inverse(ints(1, 0), ints(1, 0)).shouldBeInstanceOf<Inverse>()
            eval(inverse(ints(1, 0), ints(1, 0))) shouldBe true
            circuit(*ints(1, 2, 0).toTypedArray()).shouldBeInstanceOf<Circuit>()
            eval(circuit(*ints(1, 2, 0).toTypedArray())) shouldBe true
            cumulative(ints(0, 1), big(1, 1), big(1, 1), BigInteger.ONE).shouldBeInstanceOf<Cumulative>()
            eval(disjunctive(ints(0, 2), big(2, 1))) shouldBe true
            eval(disjunctive(ints(0, 1), big(2, 1))) shouldBe false
        }
    })
