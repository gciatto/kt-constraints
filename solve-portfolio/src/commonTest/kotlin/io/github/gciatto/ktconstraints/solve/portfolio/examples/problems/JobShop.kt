package io.github.gciatto.ktconstraints.solve.portfolio.examples.problems

import io.github.gciatto.ktconstraints.dsl.disjunctive
import io.github.gciatto.ktconstraints.dsl.le
import io.github.gciatto.ktconstraints.dsl.max
import io.github.gciatto.ktconstraints.dsl.plus
import io.github.gciatto.ktconstraints.model.ConstraintScope
import io.github.gciatto.ktconstraints.solve.portfolio.solveAuto
import io.github.gciatto.ktconstraints.solve.result.SolveStatus
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.gciatto.kt.math.BigInteger

internal suspend fun jobShop() {
    // --8<-- [start:model]
    val scope = ConstraintScope.empty()
    val a0 = scope.intVar("A-machine-0", 0..3)
    val a1 = scope.intVar("A-machine-1", 0..3)
    val b1 = scope.intVar("B-machine-1", 0..3)
    val b0 = scope.intVar("B-machine-0", 0..3)
    scope.add(a0 + 2 le a1)
    scope.add(b1 + 2 le b0)
    val durations = listOf(BigInteger.TWO, BigInteger.ONE)
    scope.add(disjunctive(listOf(a0, b0), durations))
    scope.add(disjunctive(listOf(b1, a1), durations))
    scope.minimize(max(a1 + 1, b0 + 1))
    val model = scope.build()
    // --8<-- [end:model]

    // --8<-- [start:solve]
    val result = solveAuto(model)
    println("${result.status}: makespan = ${result.objectiveValue}")
    val assignment = requireNotNull(result.assignment)
    println(listOf(a0, a1, b1, b0).map { assignment.valueOf(it) })
    // --8<-- [end:solve]
    result.status shouldBe SolveStatus.OPTIMAL
    result.objectiveValue shouldBe BigInteger.of(3)
    listOf(a0, a1, b1, b0).map { assignment.valueOf(it) } shouldBe
        listOf(BigInteger.ZERO, BigInteger.TWO, BigInteger.ZERO, BigInteger.TWO)
}

/** Executable JobShop cookbook example. */
class JobShopSpec :
    FunSpec({
        test("minimize the makespan of two jobs on two machines") { jobShop() }
    })
