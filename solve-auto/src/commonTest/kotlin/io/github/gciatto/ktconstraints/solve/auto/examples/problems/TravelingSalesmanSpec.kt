package io.github.gciatto.ktconstraints.solve.auto.examples.problems

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
            val tour = solveTravelingSalesman(distances)
            // --8<-- [end:example]
            tour.length shouldBe BigInteger.of(10)
            val visited = mutableSetOf<Int>()
            var city = 0
            repeat(4) {
                visited.add(city) shouldBe true
                city = tour.next[city]
            }
            city shouldBe 0
            visited.size shouldBe 4
            tour.next.indices.sumOf { distances[it][tour.next[it]] } shouldBe 10
        }
    })
