package io.github.gciatto.ktconstraints.solve.auto.examples.problems

// --8<-- [start:imports]
import io.github.gciatto.ktconstraints.core.integer.IntLiteral
import io.github.gciatto.ktconstraints.core.integer.IntVar
import io.github.gciatto.ktconstraints.dsl.count
import io.github.gciatto.ktconstraints.dsl.eq
import io.github.gciatto.ktconstraints.dsl.le
import io.github.gciatto.ktconstraints.dsl.minimizeViolations
import io.github.gciatto.ktconstraints.dsl.plus
import io.github.gciatto.ktconstraints.model.ConstraintScope
import io.github.gciatto.ktconstraints.solve.auto.AutoBackend
import io.github.gciatto.ktconstraints.solve.result.SolveResult
import org.gciatto.kt.math.BigInteger
// --8<-- [end:imports]

// --8<-- [start:model]
internal fun ConstraintScope.nurseRostering(): List<List<IntVar>> {
    val work =
        List(2) { nurse ->
            List(3) { day -> intVar("work-$nurse-$day", 0..1) }
        }
    for (day in 0..2) {
        add(count(IntLiteral(1), work.map { it[day] }) eq 1)
    }
    for (shifts in work) {
        add(count(IntLiteral(1), shifts) le 2)
        for (day in 0..1) {
            add(shifts[day] + shifts[day + 1] le 1)
        }
    }
    minimizeViolations(
        (work[0][0] eq 0) to 2L,
        (work[1][0] eq 0) to 1L,
    )
    return work
}
// --8<-- [end:model]

// --8<-- [start:solve]
internal suspend fun solveNurseRostering(): Pair<SolveResult, List<List<BigInteger?>>> =
    with(ConstraintScope.empty()) {
        val work = nurseRostering()
        val result = AutoBackend.load().use { auto -> auto.create().use { it.solve(build()) } }
        println("${result.status}: penalty = ${result.objectiveValue}")
        val assignment = requireNotNull(result.assignment)
        val roster = work.map { shifts -> shifts.map { assignment.valueOf(it) } }
        println(roster)
        result to roster
    }
// --8<-- [end:solve]
