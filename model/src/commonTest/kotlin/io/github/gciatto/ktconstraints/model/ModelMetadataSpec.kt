package io.github.gciatto.ktconstraints.model

import io.github.gciatto.ktconstraints.core.Sort
import io.github.gciatto.ktconstraints.core.bool.BoolExpr
import io.github.gciatto.ktconstraints.core.bool.BoolLiteral
import io.github.gciatto.ktconstraints.core.integer.IntExpr
import io.github.gciatto.ktconstraints.core.integer.IntLiteral
import io.github.gciatto.ktconstraints.core.integer.relation.IntEquality
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/** Metadata, objective and output projection preserve model isolation. */
class ModelMetadataSpec :
    FunSpec({
        test("constraints/names-and-annotations-are-frozen") {
            ConstraintScope.empty {
                val x = intVar("x", 0..10)
                val expression = IntEquality(x, IntLiteral(2))
                val annotations = mutableMapOf("tag" to "example")
                val source = SourceLocation("example.kt", 12, 4)
                add(expression, name = "fixed-x", annotations = annotations, source = source)
                val model = buildModel()
                annotations.clear()
                runCatching { (model.entries.single().annotations as MutableMap).clear() }
                model.constraints shouldBe listOf(expression)
                model.entries.single().name shouldBe "fixed-x"
                model.entries.single().annotations shouldBe mapOf("tag" to "example")
                model.entries.single().source shouldBe source
                shouldThrow<IllegalArgumentException> { add(expression, name = "fixed-x") }
                shouldThrow<IllegalArgumentException> { add(expression, name = " ") }
            }
        }

        test("projection/defaults-to-all-and-detaches-explicit-order") {
            ConstraintScope.empty {
                val x = intVar("x", 0..10)
                val y = intVar("y", 0..10)
                buildModel().outputVariables shouldBe listOf(x, y)
                val model = project(y, x).buildModel()
                project()
                runCatching { (model.outputVariables as MutableList).clear() }
                model.outputVariables shouldBe listOf(y, x)
                buildModel().outputVariables shouldBe emptyList()
            }
        }

        test("projection/rejects-foreign-and-duplicate-identities") {
            ConstraintScope.empty {
                val x = boolVar("x")
                val foreign = ConstraintScope.empty().boolVar("x")
                shouldThrow<IllegalArgumentException> { project(foreign) }
                shouldThrow<IllegalArgumentException> { project(x, x) }
            }
        }

        test("objective/replacement-and-satisfaction-are-detached") {
            ConstraintScope.empty {
                val x = intVar("x", 0..10)
                val before = minimize(x).buildModel()
                maximize(x).buildModel().objective shouldBe Objective.Maximize(x)
                satisfy().buildModel().objective shouldBe null
                before.objective shouldBe Objective.Minimize(x)
                val foreign = ConstraintScope.empty().intVar("x", 0..10)
                shouldThrow<IllegalArgumentException> { minimize(foreign) }
                shouldThrow<IllegalArgumentException> { maximize(foreign) }
            }
        }

        test("sorts/reject-inconsistent-extension-nodes") {
            ConstraintScope.empty {
                val invalidBoolean =
                    object : BoolExpr {
                        override val sort: Sort = Sort.Integer
                    }
                val invalidInteger =
                    object : IntExpr {
                        override val sort: Sort = Sort.Bool
                    }
                shouldThrow<IllegalArgumentException> { add(invalidBoolean) }
                shouldThrow<IllegalArgumentException> { minimize(invalidInteger) }
                shouldThrow<IllegalArgumentException> { maximize(invalidInteger) }
                this
                    .add(BoolLiteral(true))
                    .buildModel()
                    .constraints.size shouldBe 1
            }
        }

        test("source/positions-are-one-based") {
            shouldThrow<IllegalArgumentException> { SourceLocation("example.kt", 0, 1) }
            shouldThrow<IllegalArgumentException> { SourceLocation("example.kt", 1, 0) }
            shouldThrow<IllegalArgumentException> { SourceLocation(" ", 1, 1) }
        }
    })
