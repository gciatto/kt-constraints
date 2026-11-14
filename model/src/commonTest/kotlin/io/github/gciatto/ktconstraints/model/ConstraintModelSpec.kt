package io.github.gciatto.ktconstraints.model

import io.github.gciatto.ktconstraints.core.IntEquality
import io.github.gciatto.ktconstraints.core.IntLiteral
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/** Model snapshots cannot be changed through their exposed collections. */
class ConstraintModelSpec :
    FunSpec({
        test("model/variables-cannot-be-cleared") {
            val scope = ConstraintScope.empty()
            val x = scope.intVar("x", 0..10)
            val y = scope.intVar("y", 0..10)
            val model = scope.build()

            runCatching { (model.variables as MutableList).clear() }

            model.variables shouldBe listOf(x, y)
            scope.build().variables shouldBe listOf(x, y)
        }

        test("model/constraints-cannot-be-cleared") {
            val scope = ConstraintScope.empty()
            val x = scope.intVar("x", 0..10)
            val first = IntEquality(x, IntLiteral(1))
            val second = IntEquality(x, IntLiteral(2))
            val model = scope.add(first).add(second).build()

            runCatching { (model.constraints as MutableList).clear() }

            model.constraints shouldBe listOf(first, second)
            scope.build().constraints shouldBe listOf(first, second)
        }
    })
