package io.github.gciatto.ktconstraints.dsl

import io.github.gciatto.ktconstraints.core.BoolLiteral
import io.github.gciatto.ktconstraints.core.GroundEvaluator
import io.github.gciatto.ktconstraints.core.IntLiteral
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/** Kotlin helpers preserve global nodes and exact evaluation. */
class GlobalDslSpec :
    FunSpec({
        test("dsl/globals") {
            GroundEvaluator.evaluate(allDifferent(IntLiteral(1), IntLiteral(2))) shouldBe true
            GroundEvaluator.evaluate(element(IntLiteral(0), IntLiteral(7))).toString() shouldBe "7"
            GroundEvaluator.evaluate(sum(IntLiteral(2), IntLiteral(3))).toString() shouldBe "5"
            GroundEvaluator.evaluate(atMost(1, BoolLiteral(true), BoolLiteral(false))) shouldBe true
            GroundEvaluator.evaluate(exactly(0, BoolLiteral(false))) shouldBe true
            GroundEvaluator.evaluate(atLeast(1, BoolLiteral(false))) shouldBe false
            GroundEvaluator.evaluate(min(IntLiteral(4), IntLiteral(-2))).toString() shouldBe "-2"
            GroundEvaluator.evaluate(max(IntLiteral(4), IntLiteral(-2))).toString() shouldBe "4"
            GroundEvaluator.evaluate(abs(IntLiteral(-3))).toString() shouldBe "3"
            GroundEvaluator.evaluate(abs(IntLiteral(5))).toString() shouldBe "5"
        }
    })
