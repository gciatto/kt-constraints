package io.github.gciatto.ktconstraints.solve.auto.examples.problems

// --8<-- [start:imports]
import io.github.gciatto.ktconstraints.core.integer.IntLiteral
import io.github.gciatto.ktconstraints.core.integer.IntVar
import io.github.gciatto.ktconstraints.dsl.allDifferent
import io.github.gciatto.ktconstraints.dsl.element
import io.github.gciatto.ktconstraints.dsl.sum
import io.github.gciatto.ktconstraints.model.ConstraintScope
import io.github.gciatto.ktconstraints.solve.auto.AutoBackend
import io.github.gciatto.ktconstraints.solve.result.SolveStatus
import io.github.gciatto.ktconstraints.solve.spi.withSolver
import org.gciatto.kt.math.BigInteger
// --8<-- [end:imports]

// --8<-- [start:model]

/** Declares one task per worker, all distinct, minimizing the total cost; returns the tasks in worker order. */
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

/** The task of each worker, in worker order, and the total cost. */
internal data class Staffing(
    val tasks: List<Int>,
    val cost: BigInteger,
)

/** Finds a minimum-cost assignment of workers to tasks. */
internal suspend fun solveAssignment(costs: List<List<Int>>): Staffing =
    ConstraintScope.empty {
        val task = assignment(costs)
        val model = buildModel()
        AutoBackend.load().use { auto ->
            auto.withSolver { solver ->
                val result = solver.solve(model)
                check(result.status == SolveStatus.OPTIMAL) { result.toString() }
                val assignment = checkNotNull(result.assignment)
                val tasks = task.map { checkNotNull(assignment.intValueOf(it)) }
                val staffing = Staffing(tasks, checkNotNull(result.objectiveValue))
                println("Tasks by worker: ${staffing.tasks}; cost: ${staffing.cost}")
                staffing
            }
        }
    }
// --8<-- [end:solve]
