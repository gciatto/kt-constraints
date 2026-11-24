package io.github.gciatto.ktconstraints.solve.auto.examples.problems

// --8<-- [start:imports]
import io.github.gciatto.ktconstraints.core.integer.IntVar
import io.github.gciatto.ktconstraints.dsl.sum
import io.github.gciatto.ktconstraints.model.ConstraintScope
import io.github.gciatto.ktconstraints.solve.auto.AutoBackend
import io.github.gciatto.ktconstraints.solve.result.SolveStatus
import io.github.gciatto.ktconstraints.solve.spi.withSolver
import org.gciatto.kt.math.BigInteger
// --8<-- [end:imports]

// --8<-- [start:model]

/** Declares a zero-one take decision per item, maximizing value within [capacity]; returns the decisions by item. */
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

/** The indices of the selected items and their total value. */
internal data class Selection(
    val items: List<Int>,
    val value: BigInteger,
)

/** Finds the most valuable selection of items that fits the knapsack. */
internal suspend fun solveKnapsack(
    weights: List<Int>,
    values: List<Int>,
    capacity: Int,
): Selection =
    ConstraintScope.empty {
        val take = knapsack(weights, values, capacity)
        val model = buildModel()
        AutoBackend.load().use { auto ->
            auto.withSolver { solver ->
                val result = solver.solve(model)
                check(result.status == SolveStatus.OPTIMAL) { result.toString() }
                val assignment = checkNotNull(result.assignment)
                val items = take.indices.filter { assignment.intValueOf(take[it]) == 1 }
                val selection = Selection(items, checkNotNull(result.objectiveValue))
                println("Items: ${selection.items}; value: ${selection.value}")
                selection
            }
        }
    }
// --8<-- [end:solve]
