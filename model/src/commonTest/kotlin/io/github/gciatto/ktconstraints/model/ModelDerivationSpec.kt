package io.github.gciatto.ktconstraints.model

import io.github.gciatto.ktconstraints.core.integer.IntLiteral
import io.github.gciatto.ktconstraints.core.integer.relation.IntEquality
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe

/** Scopes and models derived from existing ones keep variable identity and stay isolated. */
class ModelDerivationSpec :
    FunSpec({
        test("empty/block-returns-its-result") {
            val (x, model) = ConstraintScope.empty { intVar("x", 0..1).let { it to buildModel() } }
            model.variables shouldBe listOf(x)
            ConstraintModel.empty().variables.shouldBeEmpty()
            ConstraintModel.empty().constraints.shouldBeEmpty()
        }

        test("extend/keeps-variables-and-leaves-the-original-unchanged") {
            ConstraintScope.empty {
                val x = intVar("x", 0..9)
                add(IntEquality(x, IntLiteral(1)), name = "one")
                minimize(x)
                val base = buildModel()
                val extended =
                    base.extend {
                        val y = intVar("y", 0..9)
                        add(IntEquality(x, y))
                        shouldThrow<IllegalArgumentException> { intVar("x", 0..1) }
                        shouldThrow<IllegalArgumentException> { add(IntEquality(x, y), name = "one") }
                    }
                extended.variables.map { it.name } shouldBe listOf("x", "y")
                extended.variables.first() shouldBe x
                extended.constraints.size shouldBe 2
                extended.objective shouldBe Objective.Minimize(x)
                base.variables shouldBe listOf(x)
                base.constraints.size shouldBe 1
            }
        }

        test("extend/default-projection-includes-new-variables") {
            val base = ConstraintModel.of { intVar("x", 0..1) }
            base.extend { intVar("y", 0..1) }.outputVariables.map { it.name } shouldBe listOf("x", "y")
        }

        test("extend/explicit-projection-is-kept") {
            ConstraintScope.empty {
                val x = intVar("x", 0..1)
                intVar("y", 0..1)
                project(x)
                buildModel().extend { intVar("z", 0..1) }.outputVariables shouldBe listOf(x)
            }
        }

        test("of-scope/copies-a-snapshot") {
            ConstraintScope.empty {
                val x = intVar("x", 0..1)
                val copy = ConstraintScope.of(this)
                copy.add(IntEquality(x, IntLiteral(0)))
                buildModel().constraints.shouldBeEmpty()
                copy.buildModel().constraints.size shouldBe 1
                ConstraintScope.of(copy) { buildModel().variables } shouldBe listOf(x)
            }
        }

        test("of-model/rejects-foreign-variables") {
            val foreign = ConstraintScope.empty { intVar("x", 0..1) }
            ConstraintScope.of(ConstraintModel.of { intVar("x", 0..1) }) {
                shouldThrow<IllegalArgumentException> { add(IntEquality(foreign, IntLiteral(0))) }
            }
        }
    })
