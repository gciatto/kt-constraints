package io.github.gciatto.ktconstraints.solve.portfolio.examples.problems

// --8<-- [start:imports]
import io.github.gciatto.ktconstraints.dsl.nValue
import io.github.gciatto.ktconstraints.dsl.neq
import io.github.gciatto.ktconstraints.model.ConstraintScope
import io.github.gciatto.ktconstraints.solve.portfolio.solveAuto
import io.github.gciatto.ktconstraints.solve.result.SolveStatus
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
// --8<-- [end:imports]

internal object GraphColoring {
    suspend fun run() {
        // --8<-- [start:model]
        val edges = listOf(0 to 1, 1 to 2, 2 to 0, 2 to 3)
        val scope = ConstraintScope.empty()
        val colors = List(4) { scope.intVar("color-$it", 0..3) }
        edges.forEach { (from, to) -> scope.add(colors[from] neq colors[to]) }
        scope.minimize(nValue(colors))
        // --8<-- [end:model]
        // --8<-- [start:solve]
        val result = solveAuto(scope.build())
        check(result.status == SolveStatus.OPTIMAL)
        val assignment = requireNotNull(result.assignment)
        val coloring = colors.map { requireNotNull(assignment.intValueOf(it)) }
        println("Colours: ${result.objectiveValue}")
        // --8<-- [end:solve]
        result.objectiveValue.toString() shouldBe "3"
        coloring.toSet().size shouldBe 3
        edges.forEach { (from, to) -> (coloring[from] != coloring[to]) shouldBe true }
    }
}

/** Checks minimum colouring without depending on the solver's colour names. */
class GraphColoringSpec :
    FunSpec({
        test("minimize colours on a triangle with a tail") { GraphColoring.run() }
    })
