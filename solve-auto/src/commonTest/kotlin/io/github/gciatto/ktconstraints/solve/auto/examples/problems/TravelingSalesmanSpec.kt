package io.github.gciatto.ktconstraints.solve.auto.examples.problems

import io.github.gciatto.ktconstraints.solve.result.SolveStatus
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.gciatto.kt.math.BigInteger

/** Executable TravelingSalesman cookbook example. */
class TravelingSalesmanSpec :
    FunSpec({
        test("minimize a four-city Hamiltonian tour") {
            // --8<-- [start:example]
            val distances =
                listOf(
                    listOf(0, 2, 9, 3),
                    listOf(2, 0, 3, 9),
                    listOf(9, 3, 0, 2),
                    listOf(3, 9, 2, 0),
                )
            val (result, successors) = solveTravelingSalesman(distances)
            // --8<-- [end:example]
            result.status shouldBe SolveStatus.OPTIMAL
            result.objectiveValue shouldBe BigInteger.of(10)
            val visited = mutableSetOf<Int>()
            var city = 0
            repeat(4) {
                visited.add(city) shouldBe true
                city = successors[city]
            }
            city shouldBe 0
            visited.size shouldBe 4
            successors.indices.sumOf { distances[it][successors[it]] } shouldBe 10
        }
    })
