package io.github.gciatto.ktconstraints.solve.auto.examples.tutorials

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe

// --8<-- [start:platform-test]
internal suspend fun verifiedFourQueens(): List<Int> {
    val board = solveFourQueens()
    check(board in listOf(listOf(1, 3, 0, 2), listOf(2, 0, 3, 1)))
    return board
}
// --8<-- [end:platform-test]

/** Checks the common model with the platform defaults. */
class NQueensSpec :
    FunSpec({
        test("solve the common four-queen model with the platform defaults") {
            listOf(listOf(1, 3, 0, 2), listOf(2, 0, 3, 1)) shouldContain verifiedFourQueens()
        }
    })

/** Checks both encodings, exhaustive counts and symmetry reduction on every target. */
class NQueensEnumerationSpec :
    FunSpec({
        test("enumerate all four-queen boards with global diagonals") {
            val boards = enumerateQueens(4)
            boards.toSet() shouldBe setOf(listOf(1, 3, 0, 2), listOf(2, 0, 3, 1))
            boards.size shouldBe 2
        }
        test("pairwise diagonals enumerate the same boards") {
            val boards = enumerateQueens(4, pairwise = true)
            boards.toSet() shouldBe setOf(listOf(1, 3, 0, 2), listOf(2, 0, 3, 1))
            boards.size shouldBe 2
        }
        test("reflection breaking keeps one four-queen board") {
            enumerateQueens(4, symmetry = true) shouldBe listOf(listOf(1, 3, 0, 2))
        }
        test("three queens have no solution") {
            enumerateQueens(3) shouldBe emptyList()
        }
    })
