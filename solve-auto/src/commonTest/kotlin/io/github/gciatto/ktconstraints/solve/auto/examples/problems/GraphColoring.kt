package io.github.gciatto.ktconstraints.solve.auto.examples.problems

// --8<-- [start:imports]
import io.github.gciatto.ktconstraints.core.integer.IntVar
import io.github.gciatto.ktconstraints.dsl.nValue
import io.github.gciatto.ktconstraints.model.ConstraintScope
import io.github.gciatto.ktconstraints.solve.auto.AutoBackend
import io.github.gciatto.ktconstraints.solve.result.SolveStatus
import io.github.gciatto.ktconstraints.solve.spi.withSolver
import org.gciatto.kt.math.BigInteger
// --8<-- [end:imports]

// --8<-- [start:model]

/** Colours four vertices so that adjacent ones differ, minimizing the colours used; returns the colours by vertex. */
internal fun ConstraintScope.graphColoring(edges: List<Pair<Int, Int>>): List<IntVar> {
    val colors = List(4) { intVar("color-$it", 0..3) }
    edges.forEach { (from, to) -> add(colors[from] neq colors[to]) }
    minimize(nValue(colors))
    return colors
}
// --8<-- [end:model]

// --8<-- [start:solve]

/** The colour of each vertex and the number of distinct colours. */
internal data class Coloring(
    val colors: List<Int>,
    val colorCount: BigInteger,
)

/** Finds a colouring that uses as few colours as possible. */
internal suspend fun solveGraphColoring(edges: List<Pair<Int, Int>>): Coloring =
    ConstraintScope.empty {
        val colors = graphColoring(edges)
        val model = buildModel()
        AutoBackend.load().use { auto ->
            auto.withSolver { solver ->
                val result = solver.solve(model)
                check(result.status == SolveStatus.OPTIMAL) { result.toString() }
                println("Colours: ${result.objectiveValue}")
                val assignment = checkNotNull(result.assignment)
                Coloring(colors.map { checkNotNull(assignment.intValueOf(it)) }, checkNotNull(result.objectiveValue))
            }
        }
    }
// --8<-- [end:solve]
