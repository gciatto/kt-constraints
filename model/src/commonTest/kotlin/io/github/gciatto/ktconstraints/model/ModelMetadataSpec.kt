package io.github.gciatto.ktconstraints.model

import io.github.gciatto.ktconstraints.core.BoolExpr
import io.github.gciatto.ktconstraints.core.BoolLiteral
import io.github.gciatto.ktconstraints.core.IntEquality
import io.github.gciatto.ktconstraints.core.IntExpr
import io.github.gciatto.ktconstraints.core.IntLiteral
import io.github.gciatto.ktconstraints.core.Sort
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/** Metadata, objective and output projection preserve model isolation. */
class ModelMetadataSpec :
    FunSpec({
        test("constraints/names-and-annotations-are-frozen") {
            val scope = ConstraintScope.empty()
            val x = scope.intVar("x", 0..10)
            val expression = IntEquality(x, IntLiteral(2))
            val annotations = mutableMapOf("tag" to "example")
            val source = SourceLocation("example.kt", 12, 4)
            scope.add(expression, name = "fixed-x", annotations = annotations, source = source)
            val model = scope.build()
            annotations.clear()
            runCatching { (model.entries.single().annotations as MutableMap).clear() }
            model.constraints shouldBe listOf(expression)
            model.entries.single().name shouldBe "fixed-x"
            model.entries.single().annotations shouldBe mapOf("tag" to "example")
            model.entries.single().source shouldBe source
            shouldThrow<IllegalArgumentException> { scope.add(expression, name = "fixed-x") }
            shouldThrow<IllegalArgumentException> { scope.add(expression, name = " ") }
        }

        test("projection/defaults-to-all-and-detaches-explicit-order") {
            val scope = ConstraintScope.empty()
            val x = scope.intVar("x", 0..10)
            val y = scope.intVar("y", 0..10)
            scope.build().outputVariables shouldBe listOf(x, y)
            val model = scope.project(y, x).build()
            scope.project()
            runCatching { (model.outputVariables as MutableList).clear() }
            model.outputVariables shouldBe listOf(y, x)
            scope.build().outputVariables shouldBe emptyList()
        }

        test("projection/rejects-foreign-and-duplicate-identities") {
            val scope = ConstraintScope.empty()
            val x = scope.boolVar("x")
            val foreign = ConstraintScope.empty().boolVar("x")
            shouldThrow<IllegalArgumentException> { scope.project(foreign) }
            shouldThrow<IllegalArgumentException> { scope.project(x, x) }
        }

        test("objective/replacement-and-satisfaction-are-detached") {
            val scope = ConstraintScope.empty()
            val x = scope.intVar("x", 0..10)
            val before = scope.minimize(x).build()
            scope.maximize(x).build().objective shouldBe Objective.Maximize(x)
            scope.satisfy().build().objective shouldBe null
            before.objective shouldBe Objective.Minimize(x)
            val foreign = ConstraintScope.empty().intVar("x", 0..10)
            shouldThrow<IllegalArgumentException> { scope.minimize(foreign) }
            shouldThrow<IllegalArgumentException> { scope.maximize(foreign) }
        }

        test("sorts/reject-inconsistent-extension-nodes") {
            val scope = ConstraintScope.empty()
            val invalidBoolean = object : BoolExpr {
                override val sort: Sort = Sort.Integer
            }
            val invalidInteger = object : IntExpr {
                override val sort: Sort = Sort.Bool
            }
            shouldThrow<IllegalArgumentException> { scope.add(invalidBoolean) }
            shouldThrow<IllegalArgumentException> { scope.minimize(invalidInteger) }
            shouldThrow<IllegalArgumentException> { scope.maximize(invalidInteger) }
            scope.add(BoolLiteral(true)).build().constraints.size shouldBe 1
        }

        test("source/positions-are-one-based") {
            shouldThrow<IllegalArgumentException> { SourceLocation("example.kt", 0, 1) }
            shouldThrow<IllegalArgumentException> { SourceLocation("example.kt", 1, 0) }
            shouldThrow<IllegalArgumentException> { SourceLocation(" ", 1, 1) }
        }
    })
