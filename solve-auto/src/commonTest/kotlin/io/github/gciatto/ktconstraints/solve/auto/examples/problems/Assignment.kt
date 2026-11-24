package io.github.gciatto.ktconstraints.solve.auto.examples.problems

// --8<-- [start:imports]
import io.github.gciatto.ktconstraints.core.integer.IntLiteral
import io.github.gciatto.ktconstraints.core.integer.IntVar
import io.github.gciatto.ktconstraints.dsl.allDifferent
import io.github.gciatto.ktconstraints.dsl.element
import io.github.gciatto.ktconstraints.dsl.sum
import io.github.gciatto.ktconstraints.model.ConstraintScope
import io.github.gciatto.ktconstraints.solve.auto.AutoBackend
import io.github.gciatto.ktconstraints.solve.result.SolveResult
import io.github.gciatto.ktconstraints.solve.result.SolveStatus
// --8<-- [end:imports]

// --8<-- [start:model]
internal fun ConstraintScope.assignment(costs: List<List<Int>>): List<IntVar> {
    val task = List(costs.size) { intVar("task-$it", costs.indices) }
    add(allDifferent(*task.toTypedArray()))
    val workerCosts =
        task.indices.map { worker ->
            element(task[worker], *costs[worker].map { IntLiteral(it) }.toTypedArray())
        }
    minimize(sum(*workerCosts.toTypedArray()))
    return task
}
// --8<-- [end:model]

// --8<-- [start:solve]
internal suspend fun solveAssignment(costs: List<List<Int>>): Pair<SolveResult, List<Int>> =
    ConstraintScope.empty {
        val task = assignment(costs)
        val result = AutoBackend.load().use { auto -> auto.create().use { it.solve(buildModel()) } }
        check(result.status == SolveStatus.OPTIMAL)
        val assignment = requireNotNull(result.assignment)
        val tasks = task.map { requireNotNull(assignment.intValueOf(it)) }
        println("Tasks by worker: $tasks; cost: ${result.objectiveValue}")
        result to tasks
    }
// --8<-- [end:solve]
