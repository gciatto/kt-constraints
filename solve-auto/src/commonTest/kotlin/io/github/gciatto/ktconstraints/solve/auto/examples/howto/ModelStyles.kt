package io.github.gciatto.ktconstraints.solve.auto.examples.howto

// --8<-- [start:imports]
import io.github.gciatto.ktconstraints.core.integer.IntLiteral
import io.github.gciatto.ktconstraints.core.integer.arithmetic.IntSum
import io.github.gciatto.ktconstraints.core.integer.relation.IntEquality
import io.github.gciatto.ktconstraints.dsl.automaton
import io.github.gciatto.ktconstraints.dsl.eq
import io.github.gciatto.ktconstraints.dsl.minimizeViolations
import io.github.gciatto.ktconstraints.dsl.plus
import io.github.gciatto.ktconstraints.dsl.regular
import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.model.ConstraintScope
import io.github.gciatto.ktconstraints.solve.SolveOptions
import io.github.gciatto.ktconstraints.solve.auto.AutoBackend
import io.github.gciatto.ktconstraints.solve.result.SolveResult
import io.github.gciatto.ktconstraints.solve.result.SolveStatus
import org.gciatto.kt.math.BigInteger
// --8<-- [end:imports]

// --8<-- [start:receiver]
internal fun receiverModel(): ConstraintModel {
    val model =
        ConstraintModel.of {
            val x = intVar("x", 0..3)
            val y = intVar("y", 0..3)
            add(x + y eq 3)
        }
    return model
}
// --8<-- [end:receiver]

// --8<-- [start:factories]
internal suspend fun solveWithFactories(): BigInteger {
    val explicitAnswer =
        ConstraintScope.empty {
            val x = intVar("x", 0..3)
            val y = intVar("y", 0..3)
            add(IntEquality(IntSum(x, y), IntLiteral(3)))
            val explicit = buildModel()
            val answer = AutoBackend.load().use { auto -> auto.createSolver().use { it.solve(explicit) } }
            val values = requireNotNull(answer.assignment)
            requireNotNull(values.valueOf(x)) + requireNotNull(values.valueOf(y))
        }
    return explicitAnswer
}
// --8<-- [end:factories]

// --8<-- [start:optimize]
internal suspend fun maximizeInteger(): Pair<SolveResult, BigInteger?> {
    val (result, xValue) =
        ConstraintScope.empty {
            val x = intVar("x", 0..3)
            val y = intVar("y", 0..3)
            add(x + y eq 3)
            maximize(x)
            val result = AutoBackend.load().use { auto -> auto.createSolver().use { it.solve(buildModel()) } }
            println("${result.status}: ${result.objectiveValue}")
            result to result.assignment?.valueOf(x)
        }
    return result to xValue
}
// --8<-- [end:optimize]

// --8<-- [start:soft]
internal suspend fun minimizePreferences(): Pair<SolveResult, BigInteger?> {
    val (result, xValue) =
        ConstraintScope.empty {
            val x = intVar("x", 0..1)
            minimizeViolations((x eq 1) to 3L, (x eq 0) to 1L)
            val result = AutoBackend.load().use { auto -> auto.createSolver().use { it.solve(buildModel()) } }
            println("${result.status}: ${result.objectiveValue}")
            result to result.assignment?.valueOf(x)
        }
    return result to xValue
}
// --8<-- [end:soft]

// --8<-- [start:limits]
internal suspend fun solveWithZeroBudget(): SolveResult {
    val model = ConstraintModel.of { intVar("x", 0..3) }
    val result = AutoBackend.load().use { auto -> auto.createSolver().use { it.solve(model, SolveOptions(timeoutMillis = 0)) } }
    return result
}
// --8<-- [end:limits]

// --8<-- [start:regular]
internal suspend fun solveShifts(symbols: List<Int>): SolveStatus =
    ConstraintScope.empty {
        val shifts = symbols.indices.map { intVar("shift-$it", 0..1) }
        val rule =
            automaton(initial = 0, accepting = setOf(0, 1)) {
                transition(from = 0, on = 0L, to = 0)
                transition(from = 0, on = 1L, to = 1)
                transition(from = 1, on = 0L, to = 0)
            }
        add(regular(rule, shifts))
        shifts.zip(symbols).forEach { (shift, symbol) -> add(shift eq symbol) }
        AutoBackend.load().use { auto -> auto.createSolver().use { it.solve(buildModel()) } }.status
    }
// --8<-- [end:regular]
