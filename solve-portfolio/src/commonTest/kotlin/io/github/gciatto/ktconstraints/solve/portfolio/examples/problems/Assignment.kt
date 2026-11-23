package io.github.gciatto.ktconstraints.solve.portfolio.examples.problems

// --8<-- [start:imports]
import io.github.gciatto.ktconstraints.core.integer.IntLiteral
import io.github.gciatto.ktconstraints.dsl.allDifferent
import io.github.gciatto.ktconstraints.dsl.element
import io.github.gciatto.ktconstraints.dsl.sum
import io.github.gciatto.ktconstraints.model.ConstraintScope
import io.github.gciatto.ktconstraints.solve.portfolio.solveAuto
import io.github.gciatto.ktconstraints.solve.result.SolveStatus
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
// --8<-- [end:imports]

internal object Assignment {
    suspend fun run() {
        // --8<-- [start:model]
        val costs = listOf(listOf(9, 2, 7), listOf(6, 4, 3), listOf(5, 8, 1))
        val scope = ConstraintScope.empty()
        val task = List(costs.size) { scope.intVar("task-$it", costs.indices) }
        scope.add(allDifferent(*task.toTypedArray()))
        val workerCosts =
            task.indices.map { worker ->
                element(task[worker], *costs[worker].map { IntLiteral(it) }.toTypedArray())
            }
        scope.minimize(sum(*workerCosts.toTypedArray()))
        // --8<-- [end:model]
        // --8<-- [start:solve]
        val result = solveAuto(scope.build())
        check(result.status == SolveStatus.OPTIMAL)
        val assignment = requireNotNull(result.assignment)
        val tasks = task.map { requireNotNull(assignment.intValueOf(it)) }
        println("Tasks by worker: $tasks; cost: ${result.objectiveValue}")
        // --8<-- [end:solve]
        tasks shouldBe listOf(1, 0, 2)
        tasks.toSet().size shouldBe costs.size
        tasks.indices.sumOf { costs[it][tasks[it]] } shouldBe 9
        result.objectiveValue.toString() shouldBe "9"
    }
}

/** Checks a minimum-cost permutation assignment through zero-based element lookup. */
class AssignmentSpec :
    FunSpec({
        test("assign three workers to three tasks") { Assignment.run() }
    })
