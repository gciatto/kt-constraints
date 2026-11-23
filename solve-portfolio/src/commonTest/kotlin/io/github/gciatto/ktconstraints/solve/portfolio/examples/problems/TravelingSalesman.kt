package io.github.gciatto.ktconstraints.solve.portfolio.examples.problems

import io.github.gciatto.ktconstraints.core.integer.IntLiteral
import io.github.gciatto.ktconstraints.dsl.circuit
import io.github.gciatto.ktconstraints.dsl.element
import io.github.gciatto.ktconstraints.dsl.sum
import io.github.gciatto.ktconstraints.model.ConstraintScope
import io.github.gciatto.ktconstraints.solve.portfolio.solveAuto
import io.github.gciatto.ktconstraints.solve.result.SolveStatus
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.gciatto.kt.math.BigInteger

internal suspend fun travelingSalesman() {
    // --8<-- [start:model]
    val scope = ConstraintScope.empty()
    val distances =
        listOf(
            listOf(0, 2, 9, 3),
            listOf(2, 0, 3, 9),
            listOf(9, 3, 0, 2),
            listOf(3, 9, 2, 0),
        )
    val next = List(4) { scope.intVar("next-$it", 0..3) }
    scope.add(circuit(*next.toTypedArray()))
    val legs =
        next.indices.map { city ->
            element(next[city], *distances[city].map { IntLiteral(it) }.toTypedArray())
        }
    scope.minimize(sum(*legs.toTypedArray()))
    val model = scope.build()
    // --8<-- [end:model]

    // --8<-- [start:solve]
    val result = solveAuto(model)
    println("${result.status}: length = ${result.objectiveValue}")
    val assignment = requireNotNull(result.assignment)
    println(next.map { assignment.valueOf(it) })
    // --8<-- [end:solve]
    result.status shouldBe SolveStatus.OPTIMAL
    result.objectiveValue shouldBe BigInteger.of(10)
    val successors = next.map { requireNotNull(assignment.valueOf(it)).toInt() }
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

/** Executable TravelingSalesman cookbook example. */
class TravelingSalesmanSpec :
    FunSpec({
        test("minimize a four-city Hamiltonian tour") { travelingSalesman() }
    })
