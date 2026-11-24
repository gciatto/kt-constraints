package io.github.gciatto.ktconstraints.solve.auto.examples.problems

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/** Checks a small Sudoku solution against rows, columns, boxes and givens. */
class SudokuSpec :
    FunSpec({
        test("solve a four by four Sudoku") {
            // --8<-- [start:givens]
            val givens =
                listOf(
                    listOf(1, 0, 0, 4),
                    listOf(0, 4, 1, 0),
                    listOf(2, 0, 0, 3),
                    listOf(0, 3, 2, 0),
                )
            // --8<-- [end:givens]
            solveSudoku(givens) shouldBe listOf(listOf(1, 2, 3, 4), listOf(3, 4, 1, 2), listOf(2, 1, 4, 3), listOf(4, 3, 2, 1))
        }
    })
