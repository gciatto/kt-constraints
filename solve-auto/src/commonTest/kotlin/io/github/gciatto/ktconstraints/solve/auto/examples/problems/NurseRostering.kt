package io.github.gciatto.ktconstraints.solve.auto.examples.problems

// --8<-- [start:imports]
import io.github.gciatto.ktconstraints.core.integer.IntLiteral
import io.github.gciatto.ktconstraints.core.integer.IntVar
import io.github.gciatto.ktconstraints.dsl.count
import io.github.gciatto.ktconstraints.dsl.minimizeViolations
import io.github.gciatto.ktconstraints.model.ConstraintScope
import io.github.gciatto.ktconstraints.solve.auto.AutoBackend
import io.github.gciatto.ktconstraints.solve.result.SolveStatus
import io.github.gciatto.ktconstraints.solve.spi.withSolver
import org.gciatto.kt.math.BigInteger
// --8<-- [end:imports]

// --8<-- [start:model]

/** Rosters two nurses over three days, minimizing weighted violated requests; returns work[nurse][day]. */
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

/** Whether each nurse works each day (`work[nurse][day]`) and the penalty of the violated requests. */
internal data class Roster(
    val work: List<List<Int>>,
    val penalty: BigInteger,
)

/** Finds a roster violating the cheapest requests. */
internal suspend fun solveNurseRostering(): Roster =
    ConstraintScope.empty {
        val work = nurseRostering()
        val model = buildModel()
        AutoBackend.load().use { auto ->
            auto.withSolver { solver ->
                val result = solver.solve(model)
                println("${result.status}: penalty = ${result.objectiveValue}")
                check(result.status == SolveStatus.OPTIMAL) { result.toString() }
                val assignment = checkNotNull(result.assignment)
                val roster = work.map { shifts -> shifts.map { checkNotNull(assignment.intValueOf(it)) } }
                println(roster)
                Roster(roster, checkNotNull(result.objectiveValue))
            }
        }
    }
// --8<-- [end:solve]
