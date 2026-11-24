package io.github.gciatto.ktconstraints.solve.auto.examples.problems

// --8<-- [start:imports]
import io.github.gciatto.ktconstraints.core.integer.IntVar
import io.github.gciatto.ktconstraints.dsl.nValue
import io.github.gciatto.ktconstraints.dsl.neq
import io.github.gciatto.ktconstraints.model.ConstraintScope
import io.github.gciatto.ktconstraints.solve.auto.AutoBackend
import io.github.gciatto.ktconstraints.solve.result.SolveResult
import io.github.gciatto.ktconstraints.solve.result.SolveStatus
// --8<-- [end:imports]

// --8<-- [start:model]
internal fun ConstraintScope.graphColoring(edges: List<Pair<Int, Int>>): List<IntVar> {
    val colors = List(4) { intVar("color-$it", 0..3) }
    edges.forEach { (from, to) -> add(colors[from] neq colors[to]) }
    minimize(nValue(colors))
    return colors
}
// --8<-- [end:model]

// --8<-- [start:solve]
internal suspend fun solveGraphColoring(edges: List<Pair<Int, Int>>): Pair<SolveResult, List<Int>> =
    ConstraintScope.empty {
        val colors = graphColoring(edges)
        val result = AutoBackend.load().use { auto -> auto.create().use { it.solve(buildModel()) } }
        check(result.status == SolveStatus.OPTIMAL)
        val assignment = requireNotNull(result.assignment)
        val coloring = colors.map { requireNotNull(assignment.intValueOf(it)) }
        println("Colours: ${result.objectiveValue}")
        result to coloring
    }
// --8<-- [end:solve]
