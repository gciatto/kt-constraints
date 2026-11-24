package io.github.gciatto.ktconstraints.solve.auto.examples.problems

// --8<-- [start:imports]
import io.github.gciatto.ktconstraints.core.integer.IntVar
import io.github.gciatto.ktconstraints.dsl.le
import io.github.gciatto.ktconstraints.dsl.sum
import io.github.gciatto.ktconstraints.dsl.times
import io.github.gciatto.ktconstraints.model.ConstraintScope
import io.github.gciatto.ktconstraints.solve.auto.AutoBackend
import io.github.gciatto.ktconstraints.solve.result.SolveResult
import io.github.gciatto.ktconstraints.solve.result.SolveStatus
// --8<-- [end:imports]

// --8<-- [start:model]
internal fun ConstraintScope.knapsack(
    weights: List<Int>,
    values: List<Int>,
    capacity: Int,
): List<IntVar> {
    val take = List(weights.size) { intVar("take-$it", 0..1) }
    val totalWeight = sum(*take.indices.map { take[it] * weights[it] }.toTypedArray())
    val totalValue = sum(*take.indices.map { take[it] * values[it] }.toTypedArray())
    add(totalWeight le capacity)
    maximize(totalValue)
    return take
}
// --8<-- [end:model]

// --8<-- [start:solve]
internal suspend fun solveKnapsack(
    weights: List<Int>,
    values: List<Int>,
    capacity: Int,
): Pair<SolveResult, List<Int>> =
    ConstraintScope.empty {
        val take = knapsack(weights, values, capacity)
        val result = AutoBackend.load().use { auto -> auto.createSolver().use { it.solve(buildModel()) } }
        check(result.status == SolveStatus.OPTIMAL)
        val assignment = requireNotNull(result.assignment)
        val selected = take.indices.filter { assignment.intValueOf(take[it]) == 1 }
        println("Items: $selected; value: ${result.objectiveValue}")
        result to selected
    }
// --8<-- [end:solve]
