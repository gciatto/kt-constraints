package io.github.gciatto.ktconstraints.solve.auto.examples.problems

// --8<-- [start:imports]
import io.github.gciatto.ktconstraints.core.integer.IntVar
import io.github.gciatto.ktconstraints.dsl.allDifferent
import io.github.gciatto.ktconstraints.dsl.eq
import io.github.gciatto.ktconstraints.model.ConstraintScope
import io.github.gciatto.ktconstraints.solve.auto.AutoBackend
import io.github.gciatto.ktconstraints.solve.result.SolveStatus
import io.github.gciatto.ktconstraints.solve.spi.withSolver
// --8<-- [end:imports]

// --8<-- [start:model]

/** Declares a four-by-four Sudoku with the [givens] (zero for blank); returns the cells as rows. */
internal fun ConstraintScope.sudoku(givens: List<List<Int>>): List<List<IntVar>> {
    val cells = List(4) { row -> List(4) { col -> intVar("cell-$row-$col", 1..4) } }
    for (row in 0 until 4) add(allDifferent(*cells[row].toTypedArray()))
    for (col in 0 until 4) add(allDifferent(*cells.map { it[col] }.toTypedArray()))
    for (boxRow in 0 until 2) {
        for (boxCol in 0 until 2) {
            val box = List(4) { offset -> cells[boxRow * 2 + offset / 2][boxCol * 2 + offset % 2] }
            add(allDifferent(*box.toTypedArray()))
        }
    }
    givens.forEachIndexed { row, values ->
        values.forEachIndexed { col, value ->
            if (value != 0) add(cells[row][col] eq value)
        }
    }
    return cells
}
// --8<-- [end:model]

// --8<-- [start:solve]

/** Solves the puzzle and returns the completed grid, row by row. */
internal suspend fun solveSudoku(givens: List<List<Int>>): List<List<Int>> =
    ConstraintScope.empty {
        val cells = sudoku(givens)
        val model = buildModel()
        AutoBackend.load().use { auto ->
            auto.withSolver { solver ->
                val result = solver.solve(model)
                check(result.status == SolveStatus.FEASIBLE) { result.toString() }
                val assignment = checkNotNull(result.assignment)
                val grid = cells.map { row -> row.map { checkNotNull(assignment.intValueOf(it)) } }
                grid.forEach { println(it.joinToString(" ")) }
                grid
            }
        }
    }
// --8<-- [end:solve]
