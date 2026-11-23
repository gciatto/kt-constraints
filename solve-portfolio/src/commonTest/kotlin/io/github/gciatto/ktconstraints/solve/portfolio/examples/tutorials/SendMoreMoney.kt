package io.github.gciatto.ktconstraints.solve.portfolio.examples.tutorials

// --8<-- [start:imports]
import io.github.gciatto.ktconstraints.dsl.allDifferent
import io.github.gciatto.ktconstraints.dsl.eq
import io.github.gciatto.ktconstraints.dsl.plus
import io.github.gciatto.ktconstraints.dsl.times
import io.github.gciatto.ktconstraints.model.ConstraintScope
import io.github.gciatto.ktconstraints.solve.portfolio.solveAuto
import io.github.gciatto.ktconstraints.solve.result.SolveStatus
// --8<-- [end:imports]

// --8<-- [start:entry]
internal suspend fun sendMoreMoney(): Pair<List<Int>, SolveStatus> {
// --8<-- [end:entry]
    // --8<-- [start:variables]
    val scope = ConstraintScope.empty()
    val s = scope.intVar("S", 1..9)
    val e = scope.intVar("E", 0..9)
    val n = scope.intVar("N", 0..9)
    val d = scope.intVar("D", 0..9)
    val m = scope.intVar("M", 1..9)
    val o = scope.intVar("O", 0..9)
    val r = scope.intVar("R", 0..9)
    val y = scope.intVar("Y", 0..9)
    // --8<-- [end:variables]

    // --8<-- [start:model]
    scope.add(allDifferent(s, e, n, d, m, o, r, y))
    val send = s * 1000 + e * 100 + n * 10 + d
    val more = m * 1000 + o * 100 + r * 10 + e
    val money = m * 10000 + o * 1000 + n * 100 + e * 10 + y
    scope.add(send + more eq money)
    val model = scope.build()
    // --8<-- [end:model]

    // --8<-- [start:solve]
    val result = solveAuto(model)
    println("${result.status} by ${result.descriptor.id}")
    check(result.status == SolveStatus.FEASIBLE) { result.toString() }
    val assignment = checkNotNull(result.assignment)
    val digits = listOf(s, e, n, d, m, o, r, y).map { checkNotNull(assignment.intValueOf(it)) }
    println("S,E,N,D,M,O,R,Y = $digits")
    // --8<-- [end:solve]

    // --8<-- [start:twist]
    scope.add(d eq 6)
    val impossible = solveAuto(scope.build())
    println(impossible.status) // INFEASIBLE
    // --8<-- [end:twist]
    // --8<-- [start:end]
    return digits to impossible.status
}
// --8<-- [end:end]
