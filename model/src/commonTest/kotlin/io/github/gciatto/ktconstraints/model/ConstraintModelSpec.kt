package io.github.gciatto.ktconstraints.model

import io.github.gciatto.ktconstraints.core.integer.IntLiteral
import io.github.gciatto.ktconstraints.core.integer.relation.IntEquality
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/** Model snapshots cannot be changed through their exposed collections. */
class ConstraintModelSpec :
    FunSpec({
        test("model/variables-cannot-be-cleared") {
            ConstraintScope.empty {
                val x = intVar("x", 0..10)
                val y = intVar("y", 0..10)
                val model = buildModel()

                runCatching { (model.variables as MutableList).clear() }

                model.variables shouldBe listOf(x, y)
                buildModel().variables shouldBe listOf(x, y)
            }
        }

        test("model/constraints-cannot-be-cleared") {
            ConstraintScope.empty {
                val x = intVar("x", 0..10)
                val first = IntEquality(x, IntLiteral(1))
                val second = IntEquality(x, IntLiteral(2))
                val model = add(first).add(second).buildModel()

                runCatching { (model.constraints as MutableList).clear() }

                model.constraints shouldBe listOf(first, second)
                buildModel().constraints shouldBe listOf(first, second)
            }
        }
    })
