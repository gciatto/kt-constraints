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
import io.github.gciatto.ktconstraints.solve.spi.withSolver
// --8<-- [end:imports]

// --8<-- [start:model]

/** Declares SEND + MORE = MONEY and returns its letters, keyed by character. */
internal fun ConstraintScope.sendMoreMoney(): Map<Char, IntVar> {
    val s = intVar("S", 1..9)
    val e = intVar("E", 0..9)
    val n = intVar("N", 0..9)
    val d = intVar("D", 0..9)
    val m = intVar("M", 1..9)
    val o = intVar("O", 0..9)
    val r = intVar("R", 0..9)
    val y = intVar("Y", 0..9)
    add(allDifferent(s, e, n, d, m, o, r, y))
    val send = s * 1000 + e * 100 + n * 10 + d
    val more = m * 1000 + o * 100 + r * 10 + e
    val money = m * 10000 + o * 1000 + n * 100 + e * 10 + y
    add(send + more eq money)
    return mapOf('S' to s, 'E' to e, 'N' to n, 'D' to d, 'M' to m, 'O' to o, 'R' to r, 'Y' to y)
}
// --8<-- [end:model]

// --8<-- [start:solve]

/** Solves the puzzle and returns the digit of each letter. */
internal suspend fun solveSendMoreMoney(): Map<Char, Int> =
    ConstraintScope.empty {
        val letters = sendMoreMoney()
        val model = buildModel()
        AutoBackend.load().use { auto ->
            auto.withSolver { solver ->
                val result = solver.solve(model)
                println("${result.status} by ${result.descriptor.id}")
                check(result.status == SolveStatus.FEASIBLE) { result.toString() }
                val assignment = checkNotNull(result.assignment)
                letters
                    .mapValues { (_, letter) -> checkNotNull(assignment.intValueOf(letter)) }
                    .also { println(it) } // {S=9, E=5, N=6, D=7, M=1, O=0, R=8, Y=2}
            }
        }
    }
// --8<-- [end:solve]

// --8<-- [start:twist]

/** Solves the puzzle again with an extra constraint fixing the digit of D. */
internal suspend fun solveSendMoreMoneyWithD(digit: Int): SolveStatus =
    ConstraintScope.empty {
        val d = sendMoreMoney().getValue('D')
        val puzzle = buildModel()
        val twisted = puzzle.extend { add(d eq digit) }
        AutoBackend.load().use { auto ->
            auto.withSolver { solver -> solver.solve(twisted).status }
        }
    }
// --8<-- [end:twist]
