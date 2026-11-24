package io.github.gciatto.ktconstraints.solve.auto.examples.tutorials

// --8<-- [start:imports]
import io.github.gciatto.ktconstraints.core.integer.IntVar
import io.github.gciatto.ktconstraints.dsl.allDifferent
import io.github.gciatto.ktconstraints.dsl.eq
import io.github.gciatto.ktconstraints.dsl.plus
import io.github.gciatto.ktconstraints.dsl.times
import io.github.gciatto.ktconstraints.model.ConstraintScope
import io.github.gciatto.ktconstraints.solve.auto.AutoBackend
import io.github.gciatto.ktconstraints.solve.result.SolveStatus
// --8<-- [end:imports]

// --8<-- [start:model]
internal fun ConstraintScope.sendMoreMoney(): List<IntVar> {
    // --8<-- [start:variables]
    val s = intVar("S", 1..9)
    val e = intVar("E", 0..9)
    val n = intVar("N", 0..9)
    val d = intVar("D", 0..9)
    val m = intVar("M", 1..9)
    val o = intVar("O", 0..9)
    val r = intVar("R", 0..9)
    val y = intVar("Y", 0..9)
    // --8<-- [end:variables]
    add(allDifferent(s, e, n, d, m, o, r, y))
    val send = s * 1000 + e * 100 + n * 10 + d
    val more = m * 1000 + o * 100 + r * 10 + e
    val money = m * 10000 + o * 1000 + n * 100 + e * 10 + y
    add(send + more eq money)
    return listOf(s, e, n, d, m, o, r, y)
}
// --8<-- [end:model]

// --8<-- [start:solve]
internal suspend fun solveSendMoreMoney(): Pair<List<Int>, SolveStatus> =
    ConstraintScope.empty {
        val letters = sendMoreMoney()
        val model = buildModel()
        val result = AutoBackend.load().use { auto -> auto.create().use { it.solve(model) } }
        println("${result.status} by ${result.descriptor.id}")
        check(result.status == SolveStatus.FEASIBLE) { result.toString() }
        val assignment = checkNotNull(result.assignment)
        val digits = letters.map { checkNotNull(assignment.intValueOf(it)) }
        println("S,E,N,D,M,O,R,Y = $digits")

        // --8<-- [start:twist]
        add(letters[3] eq 6)
        val impossible = AutoBackend.load().use { auto -> auto.create().use { it.solve(buildModel()) } }
        println(impossible.status) // INFEASIBLE
        // --8<-- [end:twist]
        digits to impossible.status
    }
// --8<-- [end:solve]
