package io.github.gciatto.ktconstraints.solve.auto.examples.problems

// --8<-- [start:imports]
import io.github.gciatto.ktconstraints.dsl.le
import io.github.gciatto.ktconstraints.dsl.sum
import io.github.gciatto.ktconstraints.dsl.times
import io.github.gciatto.ktconstraints.model.ConstraintScope
import io.github.gciatto.ktconstraints.solve.auto.solveAuto
import io.github.gciatto.ktconstraints.solve.result.SolveStatus
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
// --8<-- [end:imports]

internal object Knapsack {
    suspend fun run() {
        // --8<-- [start:model]
        val weights = listOf(2, 3, 4)
        val values = listOf(3, 4, 5)
        val capacity = 5
        val scope = ConstraintScope.empty()
        val take = List(weights.size) { scope.intVar("take-$it", 0..1) }
        val totalWeight = sum(*take.indices.map { take[it] * weights[it] }.toTypedArray())
        val totalValue = sum(*take.indices.map { take[it] * values[it] }.toTypedArray())
        scope.add(totalWeight le capacity)
        scope.maximize(totalValue)
        // --8<-- [end:model]
        // --8<-- [start:solve]
        val result = solveAuto(scope.build())
        check(result.status == SolveStatus.OPTIMAL)
        val assignment = requireNotNull(result.assignment)
        val selected = take.indices.filter { assignment.intValueOf(take[it]) == 1 }
        println("Items: $selected; value: ${result.objectiveValue}")
        // --8<-- [end:solve]
        selected shouldBe listOf(0, 1)
        selected.sumOf { weights[it] } shouldBe capacity
        selected.sumOf { values[it] } shouldBe 7
        result.objectiveValue.toString() shouldBe "7"
    }
}

/** Checks the optimal selection and its capacity independently of the objective. */
class KnapsackSpec :
    FunSpec({
        test("maximize value in a zero-one knapsack") { Knapsack.run() }
    })
