package io.github.gciatto.ktconstraints.solve.auto.examples.problems

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/** Checks minimum colouring without depending on the solver's colour names. */
class GraphColoringSpec :
    FunSpec({
        test("minimize colours on a triangle with a tail") {
            // --8<-- [start:example]
            val edges = listOf(0 to 1, 1 to 2, 2 to 0, 2 to 3)
            val (result, coloring) = solveGraphColoring(edges)
            // --8<-- [end:example]
            result.objectiveValue.toString() shouldBe "3"
            coloring.toSet().size shouldBe 3
            edges.forEach { (from, to) -> (coloring[from] != coloring[to]) shouldBe true }
        }
    })
