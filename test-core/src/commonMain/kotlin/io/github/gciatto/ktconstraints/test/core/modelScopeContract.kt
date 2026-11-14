package io.github.gciatto.ktconstraints.test.core

import io.github.gciatto.ktconstraints.core.IntEquality
import io.github.gciatto.ktconstraints.core.IntLiteral
import io.github.gciatto.ktconstraints.core.IntSum
import io.github.gciatto.ktconstraints.model.ConstraintScope
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.funSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll

/** Reusable ownership and variable-renaming contract with fresh scopes in every generated case. */
@OptIn(ExperimentalKotest::class)
fun modelScopeContract(create: () -> ConstraintScope) =
    funSpec {
        test("model-contract/renaming-preserves-structure-without-aliasing-scopes") {
            checkAll(PropTestConfig(iterations = 50, seed = 155921L), Arb.int()) { suffix ->
                val first = create()
                val second = create()
                val x = first.intVar("x$suffix", 0..10)
                val renamed = second.intVar("renamed$suffix", 0..10)
                (x == renamed) shouldBe false
                val expression = IntEquality(IntSum(x, IntLiteral(1)), IntLiteral(2))
                first.add(expression)
                shouldThrow<IllegalArgumentException> { second.add(expression) }
                val snapshot = first.build()
                first.add(IntEquality(x, IntLiteral(1)))
                snapshot.constraints shouldBe listOf(expression)
                second.add(IntEquality(IntSum(renamed, IntLiteral(1)), IntLiteral(2)))
                second
                    .build()
                    .constraints
                    .single()
                    .variables shouldBe setOf(renamed)
            }
        }
    }
