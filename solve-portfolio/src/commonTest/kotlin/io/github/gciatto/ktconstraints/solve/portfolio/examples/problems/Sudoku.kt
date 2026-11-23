package io.github.gciatto.ktconstraints.solve.portfolio.examples.problems

// --8<-- [start:imports]
import io.github.gciatto.ktconstraints.dsl.allDifferent
import io.github.gciatto.ktconstraints.dsl.eq
import io.github.gciatto.ktconstraints.model.ConstraintScope
import io.github.gciatto.ktconstraints.solve.portfolio.solveAuto
import io.github.gciatto.ktconstraints.solve.result.SolveStatus
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
// --8<-- [end:imports]

internal object Sudoku {
    suspend fun run() {
        // --8<-- [start:model]
        val givens =
            listOf(
                listOf(1, 0, 0, 4),
                listOf(0, 4, 1, 0),
                listOf(2, 0, 0, 3),
                listOf(0, 3, 2, 0),
            )
        val scope = ConstraintScope.empty()
        val cells = List(4) { row -> List(4) { col -> scope.intVar("cell-$row-$col", 1..4) } }
        for (row in 0 until 4) scope.add(allDifferent(*cells[row].toTypedArray()))
        for (col in 0 until 4) scope.add(allDifferent(*cells.map { it[col] }.toTypedArray()))
        for (boxRow in 0 until 2) {
            for (boxCol in 0 until 2) {
                val box = List(4) { offset -> cells[boxRow * 2 + offset / 2][boxCol * 2 + offset % 2] }
                scope.add(allDifferent(*box.toTypedArray()))
            }
        }
        givens.forEachIndexed { row, values ->
            values.forEachIndexed { col, value ->
                if (value != 0) scope.add(cells[row][col] eq value)
            }
        }
        // --8<-- [end:model]
        // --8<-- [start:solve]
        val result = solveAuto(scope.build())
        check(result.status == SolveStatus.FEASIBLE)
        val assignment = requireNotNull(result.assignment)
        val grid = cells.map { row -> row.map { requireNotNull(assignment.intValueOf(it)) } }
        grid.forEach { println(it.joinToString(" ")) }
        // --8<-- [end:solve]
        grid shouldBe listOf(listOf(1, 2, 3, 4), listOf(3, 4, 1, 2), listOf(2, 1, 4, 3), listOf(4, 3, 2, 1))
    }
}

/** Checks a small Sudoku solution against rows, columns, boxes and givens. */
class SudokuSpec :
    FunSpec({
        test("solve a four by four Sudoku") { Sudoku.run() }
    })
