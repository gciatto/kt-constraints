package io.github.gciatto.ktconstraints.model

import io.github.gciatto.ktconstraints.core.IntEquality
import io.github.gciatto.ktconstraints.core.IntLiteral
import io.github.gciatto.ktconstraints.core.IntSum
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/** Frozen models and variable ownership contract. */
class ConstraintScopeSpec :
    FunSpec({
        test("scope/snapshot-is-detached") {
            val scope = ConstraintScope.empty()
            val x = scope.intVar("x", 0..10)
            val y = scope.intVar("y", 0..10)
            scope.add(IntEquality(IntSum(x, y), IntLiteral(10)))
            val before = scope.build()
            scope.add(IntEquality(x, IntLiteral(2)))
            before.constraints.size shouldBe 1
            scope.build().constraints.size shouldBe 2
        }

        test("scope/variable-identities-are-distinct") {
            val a = ConstraintScope.empty().intVar("x", 0..10)
            val b = ConstraintScope.empty().intVar("x", 0..10)
            (a == b) shouldBe false
        }

        test("scope/foreign-variables-are-rejected") {
            val first = ConstraintScope.empty()
            val second = ConstraintScope.empty()
            val foreign = second.intVar("foreign", 1..10)
            first.intVar("local", 1..10)
            shouldThrow<IllegalArgumentException> {
                first.add(IntEquality(foreign, IntLiteral(3)))
            }
        }

        test("scope/duplicate-variable-names-are-rejected") {
            val scope = ConstraintScope.empty()
            scope.boolVar("x")
            shouldThrow<IllegalArgumentException> { scope.intVar("x", 0..10) }
        }
    })
