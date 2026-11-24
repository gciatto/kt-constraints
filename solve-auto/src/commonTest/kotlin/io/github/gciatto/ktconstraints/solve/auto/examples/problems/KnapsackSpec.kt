package io.github.gciatto.ktconstraints.solve.auto.examples.problems

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.gciatto.kt.math.BigInteger

/** Checks the optimal selection and its capacity independently of the objective. */
class KnapsackSpec :
    FunSpec({
        test("maximize value in a zero-one knapsack") {
            // --8<-- [start:example]
            val weights = listOf(2, 3, 4)
            val values = listOf(3, 4, 5)
            val capacity = 5
            val selection = solveKnapsack(weights, values, capacity)
            // --8<-- [end:example]
            val selected = selection.items
            selected shouldBe listOf(0, 1)
            selected.sumOf { weights[it] } shouldBe capacity
            selected.sumOf { values[it] } shouldBe 7
            selection.value shouldBe BigInteger.of(7)
        }
    })
