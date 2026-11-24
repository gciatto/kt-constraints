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
import io.github.gciatto.ktconstraints.solve.spi.withSolver
import org.gciatto.kt.math.BigInteger
// --8<-- [end:imports]

// --8<-- [start:receiver]

/** Builds x + y = 3 in a receiver block. */
internal fun receiverModel(): ConstraintModel =
    ConstraintModel.of {
        val x = intVar("x", 0..3)
        val y = intVar("y", 0..3)
        add(x + y eq 3)
    }
// --8<-- [end:receiver]

// --8<-- [start:factories]

/** Builds x + y = 3 from explicit expression nodes, solves it and returns x + y. */
internal suspend fun solveWithFactories(): BigInteger =
    ConstraintScope.empty {
        val x = intVar("x", 0..3)
        val y = intVar("y", 0..3)
        add(IntEquality(IntSum(x, y), IntLiteral(3)))
        val model = buildModel()
        AutoBackend.load().use { auto ->
            auto.withSolver { solver ->
                val values = checkNotNull(solver.solve(model).assignment)
                checkNotNull(values.valueOf(x)) + checkNotNull(values.valueOf(y))
            }
        }
    }
// --8<-- [end:factories]

// --8<-- [start:optimize]

/** Maximizes x subject to x + y = 3. */
internal suspend fun maximizeInteger(): SolveResult =
    ConstraintScope.empty {
        val x = intVar("x", 0..3)
        val y = intVar("y", 0..3)
        add(x + y eq 3)
        maximize(x)
        val model = buildModel()
        AutoBackend.load().use { auto ->
            auto.withSolver { solver ->
                val result = solver.solve(model)
                println("${result.status}: ${result.objectiveValue}")
                result
            }
        }
    }
// --8<-- [end:optimize]

// --8<-- [start:soft]

/** Minimizes the weight of the violated preferences x = 1 (weight 3) and x = 0 (weight 1). */
internal suspend fun minimizePreferences(): SolveResult =
    ConstraintScope.empty {
        val x = intVar("x", 0..1)
        minimizeViolations((x eq 1) to 3L, (x eq 0) to 1L)
        val model = buildModel()
        AutoBackend.load().use { auto ->
            auto.withSolver { solver ->
                val result = solver.solve(model)
                println("${result.status}: ${result.objectiveValue}")
                result
            }
        }
    }
// --8<-- [end:soft]

// --8<-- [start:limits]

/** Solves a trivial model with a zero time budget. */
internal suspend fun solveWithZeroBudget(): SolveResult {
    val model = ConstraintModel.of { intVar("x", 0..3) }
    return AutoBackend.load().use { auto ->
        auto.withSolver { solver -> solver.solve(model, SolveOptions(timeoutMillis = 0)) }
    }
}
// --8<-- [end:limits]

// --8<-- [start:regular]

/** Checks whether the shift [symbols] (1 = work, 0 = rest) never contain two working days in a row. */
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
        val model = buildModel()
        AutoBackend.load().use { auto ->
            auto.withSolver { solver -> solver.solve(model).status }
        }
    }
// --8<-- [end:regular]
