package io.github.gciatto.ktconstraints.dsl

import io.github.gciatto.ktconstraints.core.GroundEvaluator
import io.github.gciatto.ktconstraints.core.IntEquality
import io.github.gciatto.ktconstraints.core.IntLiteral
import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/** Verifies that infix operators build expressions, rather than computing numeric results. */
class IntDslSpec :
    FunSpec({
        test("dsl/symbolic-equality") {
            val model =
                ConstraintModel.of {
                    val x = intVar("x", 1..10)
                    val y = intVar("y", 1..10)
                    add(x + y eq 10)
                }
            model.constraints.size shouldBe 1
            (model.constraints.single() is IntEquality) shouldBe true
        }

        test("dsl/arithmetic-and-comparisons-have-exact-semantics") {
            val x = IntLiteral("9223372036854775808")
            GroundEvaluator.evaluate((x * 2 - x + 1) gt x) shouldBe true
            GroundEvaluator.evaluate((-x) lt 0) shouldBe true
            GroundEvaluator.evaluate(x ge x) shouldBe true
            GroundEvaluator.evaluate(x le x) shouldBe true
            GroundEvaluator.evaluate(x neq (x + 1)) shouldBe true
            GroundEvaluator.evaluate(x eq Long.MAX_VALUE) shouldBe false
        }
    })
