package io.github.gciatto.ktconstraints.solve.portfolio.examples.problems

import io.github.gciatto.ktconstraints.core.integer.IntLiteral
import io.github.gciatto.ktconstraints.dsl.count
import io.github.gciatto.ktconstraints.dsl.eq
import io.github.gciatto.ktconstraints.dsl.le
import io.github.gciatto.ktconstraints.dsl.minimizeViolations
import io.github.gciatto.ktconstraints.dsl.plus
import io.github.gciatto.ktconstraints.model.ConstraintScope
import io.github.gciatto.ktconstraints.solve.portfolio.solveAuto
import io.github.gciatto.ktconstraints.solve.result.SolveStatus
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.gciatto.kt.math.BigInteger

internal suspend fun nurseRostering() {
    // --8<-- [start:model]
    val scope = ConstraintScope.empty()
    val work =
        List(2) { nurse ->
            List(3) { day -> scope.intVar("work-$nurse-$day", 0..1) }
        }
    for (day in 0..2) {
        scope.add(count(IntLiteral(1), work.map { it[day] }) eq 1)
    }
    for (shifts in work) {
        scope.add(count(IntLiteral(1), shifts) le 2)
        for (day in 0..1) {
            scope.add(shifts[day] + shifts[day + 1] le 1)
        }
    }
    scope.minimizeViolations(
        (work[0][0] eq 0) to 2L,
        (work[1][0] eq 0) to 1L,
    )
    val model = scope.build()
    // --8<-- [end:model]

    // --8<-- [start:solve]
    val result = solveAuto(model)
    println("${result.status}: penalty = ${result.objectiveValue}")
    val assignment = requireNotNull(result.assignment)
    val roster = work.map { shifts -> shifts.map { assignment.valueOf(it) } }
    println(roster)
    // --8<-- [end:solve]
    result.status shouldBe SolveStatus.OPTIMAL
    result.objectiveValue shouldBe BigInteger.ONE
    roster shouldBe
        listOf(
            listOf(BigInteger.ZERO, BigInteger.ONE, BigInteger.ZERO),
            listOf(BigInteger.ONE, BigInteger.ZERO, BigInteger.ONE),
        )
}

/** Executable NurseRostering cookbook example. */
class NurseRosteringSpec :
    FunSpec({
        test("cover three days while minimizing weighted preference violations") { nurseRostering() }
    })
