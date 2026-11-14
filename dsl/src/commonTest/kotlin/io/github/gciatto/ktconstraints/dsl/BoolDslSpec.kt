package io.github.gciatto.ktconstraints.dsl

import io.github.gciatto.ktconstraints.core.BoolLiteral
import io.github.gciatto.ktconstraints.core.GroundEvaluator
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/** Boolean operators build ordinary typed nodes. */
class BoolDslSpec :
    FunSpec({
        test("dsl/boolean-operators") {
            val a = BoolLiteral(true)
            val b = BoolLiteral(false)
            GroundEvaluator.evaluate((a or b) and !b) shouldBe true
            GroundEvaluator.evaluate(a implies b) shouldBe false
            GroundEvaluator.evaluate(a eq !b) shouldBe true
            GroundEvaluator.evaluate(a neq b) shouldBe true
        }
    })
