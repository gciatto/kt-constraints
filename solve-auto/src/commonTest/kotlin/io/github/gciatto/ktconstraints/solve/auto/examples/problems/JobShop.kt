package io.github.gciatto.ktconstraints.solve.auto.examples.problems

// --8<-- [start:imports]
import io.github.gciatto.ktconstraints.core.integer.IntVar
import io.github.gciatto.ktconstraints.dsl.disjunctive
import io.github.gciatto.ktconstraints.dsl.le
import io.github.gciatto.ktconstraints.dsl.max
import io.github.gciatto.ktconstraints.dsl.plus
import io.github.gciatto.ktconstraints.model.ConstraintScope
import io.github.gciatto.ktconstraints.solve.auto.AutoBackend
import io.github.gciatto.ktconstraints.solve.result.SolveResult
import org.gciatto.kt.math.BigInteger
// --8<-- [end:imports]

// --8<-- [start:model]
internal fun ConstraintScope.jobShop(): List<IntVar> {
    val a0 = intVar("A-machine-0", 0..3)
    val a1 = intVar("A-machine-1", 0..3)
    val b1 = intVar("B-machine-1", 0..3)
    val b0 = intVar("B-machine-0", 0..3)
    add(a0 + 2 le a1)
    add(b1 + 2 le b0)
    val durations = listOf(BigInteger.TWO, BigInteger.ONE)
    add(disjunctive(listOf(a0, b0), durations))
    add(disjunctive(listOf(b1, a1), durations))
    minimize(max(a1 + 1, b0 + 1))
    return listOf(a0, a1, b1, b0)
}
// --8<-- [end:model]

// --8<-- [start:solve]
internal suspend fun solveJobShop(): Pair<SolveResult, List<BigInteger?>> =
    with(ConstraintScope.empty()) {
        val tasks = jobShop()
        val result = AutoBackend.load().use { auto -> auto.create().use { it.solve(build()) } }
        println("${result.status}: makespan = ${result.objectiveValue}")
        val assignment = requireNotNull(result.assignment)
        val starts = tasks.map { assignment.valueOf(it) }
        println(starts)
        result to starts
    }
// --8<-- [end:solve]
