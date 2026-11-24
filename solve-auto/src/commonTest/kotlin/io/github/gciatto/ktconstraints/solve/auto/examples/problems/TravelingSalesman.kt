package io.github.gciatto.ktconstraints.solve.auto.examples.problems

// --8<-- [start:imports]
import io.github.gciatto.ktconstraints.core.integer.IntLiteral
import io.github.gciatto.ktconstraints.core.integer.IntVar
import io.github.gciatto.ktconstraints.dsl.circuit
import io.github.gciatto.ktconstraints.dsl.element
import io.github.gciatto.ktconstraints.dsl.sum
import io.github.gciatto.ktconstraints.model.ConstraintScope
import io.github.gciatto.ktconstraints.solve.auto.AutoBackend
import io.github.gciatto.ktconstraints.solve.result.SolveResult
// --8<-- [end:imports]

// --8<-- [start:model]
internal fun ConstraintScope.travelingSalesman(distances: List<List<Int>>): List<IntVar> {
    val next = List(4) { intVar("next-$it", 0..3) }
    add(circuit(*next.toTypedArray()))
    val legs =
        next.indices.map { city ->
            element(next[city], *distances[city].map { IntLiteral(it) }.toTypedArray())
        }
    minimize(sum(*legs.toTypedArray()))
    return next
}
// --8<-- [end:model]

// --8<-- [start:solve]
internal suspend fun solveTravelingSalesman(distances: List<List<Int>>): Pair<SolveResult, List<Int>> =
    ConstraintScope.empty {
        val next = travelingSalesman(distances)
        val result = AutoBackend.load().use { auto -> auto.createSolver().use { it.solve(buildModel()) } }
        println("${result.status}: length = ${result.objectiveValue}")
        val assignment = requireNotNull(result.assignment)
        println(next.map { assignment.valueOf(it) })
        val successors = next.map { requireNotNull(assignment.valueOf(it)).toInt() }
        result to successors
    }
// --8<-- [end:solve]
