package io.github.gciatto.ktconstraints.solve.auto.examples.tutorials

// --8<-- [start:imports]
import io.github.gciatto.ktconstraints.core.integer.IntVar
import io.github.gciatto.ktconstraints.dsl.allDifferent
import io.github.gciatto.ktconstraints.dsl.lt
import io.github.gciatto.ktconstraints.dsl.minus
import io.github.gciatto.ktconstraints.dsl.neq
import io.github.gciatto.ktconstraints.dsl.plus
import io.github.gciatto.ktconstraints.model.ConstraintScope
import io.github.gciatto.ktconstraints.solve.auto.AutoBackend
import io.github.gciatto.ktconstraints.solve.result.SolveStatus
import io.github.gciatto.ktconstraints.solve.result.TerminationReason
import io.github.gciatto.ktconstraints.solve.stream.SolutionFound
import io.github.gciatto.ktconstraints.solve.stream.SolveCompleted
// --8<-- [end:imports]

// --8<-- [start:board]
internal fun ConstraintScope.queensBoard(size: Int): List<IntVar> {
    require(size > 0)
    val rows = List(size) { column -> intVar("q$column", 0 until size) }
    add(allDifferent(*rows.toTypedArray()))
    return rows
}
// --8<-- [end:board]

// --8<-- [start:diagonal-globals]
internal fun ConstraintScope.diagonalGlobals(rows: List<IntVar>) {
    add(allDifferent(*rows.mapIndexed { column, row -> row + column }.toTypedArray()))
    add(allDifferent(*rows.mapIndexed { column, row -> row - column }.toTypedArray()))
}
// --8<-- [end:diagonal-globals]

// --8<-- [start:pairwise]
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
internal fun ConstraintScope.breakRowReflection(rows: List<IntVar>) {
    require(rows.size > 1)
    add(rows.first() lt rows.last())
}
// --8<-- [end:symmetry]

// --8<-- [start:solve]
internal suspend fun solveFourQueens(): List<Int> =
    with(ConstraintScope.empty()) {
        val rows = queensBoard(4)
        diagonalGlobals(rows)
        val result = AutoBackend.load().use { auto -> auto.create().use { it.solve(build()) } }
        println("${result.status} by ${result.descriptor.id}")
        check(result.status == SolveStatus.FEASIBLE) { result.toString() }
        val assignment = checkNotNull(result.assignment)
        rows.map { checkNotNull(assignment.intValueOf(it)) }
    }
// --8<-- [end:solve]

// --8<-- [start:enumerate]
internal suspend fun enumerateQueens(
    size: Int,
    pairwise: Boolean = false,
    symmetry: Boolean = false,
): List<List<Int>> =
    with(ConstraintScope.empty()) {
        val rows = queensBoard(size)
        if (pairwise) pairwiseDiagonals(rows) else diagonalGlobals(rows)
        if (symmetry) breakRowReflection(rows)
        val model = build()
        val boards = mutableListOf<List<Int>>()
        AutoBackend.load().use { auto ->
            auto.create().use { solver ->
                solver.solutions(model).use { stream ->
                    while (true) {
                        when (val event = stream.next()) {
                            is SolutionFound -> boards.add(rows.map { checkNotNull(event.assignment.intValueOf(it)) })
                            is SolveCompleted -> {
                                check(event.result.terminationReason == TerminationReason.COMPLETED)
                                check(event.result.status == SolveStatus.FEASIBLE || event.result.status == SolveStatus.INFEASIBLE)
                                break
                            }
                            null -> error("Search ended without a completion event")
                        }
                    }
                }
            }
        }
        boards
    }
// --8<-- [end:enumerate]
