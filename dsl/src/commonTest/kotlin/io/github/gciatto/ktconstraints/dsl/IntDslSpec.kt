package io.github.gciatto.ktconstraints.dsl

import io.github.gciatto.ktconstraints.core.IntEquality
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
    })
