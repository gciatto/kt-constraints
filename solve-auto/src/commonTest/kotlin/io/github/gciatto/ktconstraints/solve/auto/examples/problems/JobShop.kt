package io.github.gciatto.ktconstraints.solve.auto.examples.problems

// --8<-- [start:imports]
import io.github.gciatto.ktconstraints.core.integer.IntVar
import io.github.gciatto.ktconstraints.dsl.disjunctive
import io.github.gciatto.ktconstraints.dsl.le
import io.github.gciatto.ktconstraints.dsl.max
import io.github.gciatto.ktconstraints.dsl.plus
import io.github.gciatto.ktconstraints.model.ConstraintScope
import io.github.gciatto.ktconstraints.solve.auto.AutoBackend
import io.github.gciatto.ktconstraints.solve.result.SolveStatus
import io.github.gciatto.ktconstraints.solve.spi.withSolver
import org.gciatto.kt.math.BigInteger
// --8<-- [end:imports]

// --8<-- [start:model]

/** Schedules two jobs on two machines, minimizing the makespan; returns the start of each operation by name. */
internal fun ConstraintScope.jobShop(): Map<String, IntVar> {
    val a0 = intVar("A-machine-0", 0..3)
    val a1 = intVar("A-machine-1", 0..3)
    val b1 = intVar("B-machine-1", 0..3)
    val b0 = intVar("B-machine-0", 0..3)
    add(a0 + 2 le a1)
    add(b1 + 2 le b0)
    val durations = listOf(BigInteger.TWO, BigInteger.ONE)
    add(disjunctive(listOf(a0, b0), durations))
    add(disjunctive(listOf(b1, a1), durations))
    minimize(max(a1 + 1, b0 + 1))
    return listOf(a0, a1, b1, b0).associateBy { it.name }
}
// --8<-- [end:model]

// --8<-- [start:solve]

/** The start time of each operation, by name, and the makespan. */
internal data class Schedule(
    val starts: Map<String, Int>,
    val makespan: BigInteger,
)

/** Finds a schedule with the shortest makespan. */
internal suspend fun solveJobShop(): Schedule =
    ConstraintScope.empty {
        val operations = jobShop()
        val model = buildModel()
        AutoBackend.load().use { auto ->
            auto.withSolver { solver ->
                val result = solver.solve(model)
                println("${result.status}: makespan = ${result.objectiveValue}")
                check(result.status == SolveStatus.OPTIMAL) { result.toString() }
                val assignment = checkNotNull(result.assignment)
                val starts = operations.mapValues { (_, start) -> checkNotNull(assignment.intValueOf(start)) }
                println(starts)
                Schedule(starts, checkNotNull(result.objectiveValue))
            }
        }
    }
// --8<-- [end:solve]
