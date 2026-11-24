package io.github.gciatto.ktconstraints.solve.auto.examples.problems

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/** Checks a minimum-cost permutation assignment through zero-based element lookup. */
class AssignmentSpec :
    FunSpec({
        test("assign three workers to three tasks") {
            // --8<-- [start:example]
            val costs = listOf(listOf(9, 2, 7), listOf(6, 4, 3), listOf(5, 8, 1))
            val (result, tasks) = solveAssignment(costs)
            // --8<-- [end:example]
            tasks shouldBe listOf(1, 0, 2)
            tasks.toSet().size shouldBe costs.size
            tasks.indices.sumOf { costs[it][tasks[it]] } shouldBe 9
            result.objectiveValue.toString() shouldBe "9"
        }
    })
