package io.github.gciatto.ktconstraints.solve.auto.examples.tutorials

// --8<-- [start:imports]
import io.github.gciatto.ktconstraints.core.integer.IntVar
import io.github.gciatto.ktconstraints.dsl.allDifferent
import io.github.gciatto.ktconstraints.model.ConstraintScope
import io.github.gciatto.ktconstraints.solve.auto.AutoBackend
import io.github.gciatto.ktconstraints.solve.result.SolveStatus
import io.github.gciatto.ktconstraints.solve.result.TerminationReason
import io.github.gciatto.ktconstraints.solve.spi.withSolver
import io.github.gciatto.ktconstraints.solve.stream.forEachSolution
// --8<-- [end:imports]

// --8<-- [start:board]

/** Declares one queen per column, in distinct rows; returns the row variable of each column. */
internal fun ConstraintScope.queensBoard(size: Int): List<IntVar> {
    require(size > 0)
    val rows = List(size) { column -> intVar("q$column", 0 until size) }
    add(allDifferent(*rows.toTypedArray()))
    return rows
}
// --8<-- [end:board]

// --8<-- [start:diagonal-globals]

/** Keeps queens off shared diagonals with two `allDifferent` globals. */
internal fun ConstraintScope.diagonalGlobals(rows: List<IntVar>) {
    add(allDifferent(*rows.mapIndexed { column, row -> row + column }.toTypedArray()))
    add(allDifferent(*rows.mapIndexed { column, row -> row - column }.toTypedArray()))
}
// --8<-- [end:diagonal-globals]

// --8<-- [start:pairwise]

/** Keeps queens off shared diagonals with one inequality per pair of columns. */
internal fun ConstraintScope.pairwiseDiagonals(rows: List<IntVar>) {
    for (left in rows.indices) {
        for (right in left + 1 until rows.size) {
            add(rows[left] + left neq rows[right] + right)
            add(rows[left] - left neq rows[right] - right)
        }
    }
}
// --8<-- [end:pairwise]

// --8<-- [start:symmetry]

/** Discards boards mirrored top to bottom by ordering the first and last queens. */
internal fun ConstraintScope.breakRowReflection(rows: List<IntVar>) {
    require(rows.size > 1)
    add(rows.first() lt rows.last())
}
// --8<-- [end:symmetry]

// --8<-- [start:solve]

/** Solves four queens and returns the row of the queen in each column. */
internal suspend fun solveFourQueens(): List<Int> =
    ConstraintScope.empty {
        val rows = queensBoard(4)
        diagonalGlobals(rows)
        val model = buildModel()
        AutoBackend.load().use { auto ->
            auto.withSolver { solver ->
                val result = solver.solve(model)
                println("${result.status} by ${result.descriptor.id}")
                check(result.status == SolveStatus.FEASIBLE) { result.toString() }
                val assignment = checkNotNull(result.assignment)
                rows.map { checkNotNull(assignment.intValueOf(it)) }
            }
        }
    }
// --8<-- [end:solve]

// --8<-- [start:enumerate]

/** Lists every board of [size] queens, each as the row of the queen in each column. */
internal suspend fun enumerateQueens(
    size: Int,
    pairwise: Boolean = false,
    symmetry: Boolean = false,
): List<List<Int>> =
    ConstraintScope.empty {
        val rows = queensBoard(size)
        if (pairwise) pairwiseDiagonals(rows) else diagonalGlobals(rows)
        if (symmetry) breakRowReflection(rows)
        val model = buildModel()
        val boards = mutableListOf<List<Int>>()
        val result =
            AutoBackend.load().use { auto ->
                auto.withSolver { solver ->
                    solver.forEachSolution(model) { found -> boards += rows.map { checkNotNull(found.assignment.intValueOf(it)) } }
                }
            }
        // Stopping early, for example on a timeout, would leave the list incomplete.
        check(result.terminationReason == TerminationReason.COMPLETED) { result.toString() }
        boards
    }
// --8<-- [end:enumerate]
