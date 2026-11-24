package io.github.gciatto.ktconstraints.solve.auto.examples.problems

// --8<-- [start:imports]
import io.github.gciatto.ktconstraints.core.integer.IntLiteral
import io.github.gciatto.ktconstraints.core.integer.IntVar
import io.github.gciatto.ktconstraints.dsl.circuit
import io.github.gciatto.ktconstraints.dsl.element
import io.github.gciatto.ktconstraints.dsl.sum
import io.github.gciatto.ktconstraints.model.ConstraintScope
import io.github.gciatto.ktconstraints.solve.auto.AutoBackend
import io.github.gciatto.ktconstraints.solve.result.SolveStatus
import io.github.gciatto.ktconstraints.solve.spi.withSolver
import org.gciatto.kt.math.BigInteger
// --8<-- [end:imports]

// --8<-- [start:model]

/** Declares the successor of each of four cities on one tour, minimizing its length; returns successors by city. */
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

/** The successor of each city on the tour and the tour length. */
internal data class Tour(
    val next: List<Int>,
    val length: BigInteger,
)

/** Finds a shortest tour visiting every city once. */
internal suspend fun solveTravelingSalesman(distances: List<List<Int>>): Tour =
    ConstraintScope.empty {
        val next = travelingSalesman(distances)
        val model = buildModel()
        AutoBackend.load().use { auto ->
            auto.withSolver { solver ->
                val result = solver.solve(model)
                println("${result.status}: length = ${result.objectiveValue}")
                check(result.status == SolveStatus.OPTIMAL) { result.toString() }
                val assignment = checkNotNull(result.assignment)
                val successors = next.map { checkNotNull(assignment.intValueOf(it)) }
                println(successors)
                Tour(successors, checkNotNull(result.objectiveValue))
            }
        }
    }
// --8<-- [end:solve]
