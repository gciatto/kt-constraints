package io.github.gciatto.ktconstraints.solve.auto.examples.tutorials

// --8<-- [start:imports]
import io.github.gciatto.ktconstraints.core.integer.IntVar
import io.github.gciatto.ktconstraints.dsl.allDifferent
import io.github.gciatto.ktconstraints.dsl.lt
import io.github.gciatto.ktconstraints.dsl.minus
import io.github.gciatto.ktconstraints.dsl.neq
import io.github.gciatto.ktconstraints.dsl.plus
import io.github.gciatto.ktconstraints.model.ConstraintScope
import io.github.gciatto.ktconstraints.solve.SolverCapability
import io.github.gciatto.ktconstraints.solve.auto.DefaultBackends
import io.github.gciatto.ktconstraints.solve.auto.solveAuto
import io.github.gciatto.ktconstraints.solve.result.SolveStatus
import io.github.gciatto.ktconstraints.solve.result.TerminationReason
import io.github.gciatto.ktconstraints.solve.stream.SolutionFound
import io.github.gciatto.ktconstraints.solve.stream.SolveCompleted
// --8<-- [end:imports]

// --8<-- [start:board]
internal fun queensBoard(size: Int): Pair<ConstraintScope, List<IntVar>> {
    require(size > 0)
    val scope = ConstraintScope.empty()
    val rows = List(size) { column -> scope.intVar("q$column", 0 until size) }
    scope.add(allDifferent(*rows.toTypedArray()))
    return scope to rows
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
internal suspend fun solveFourQueens(): List<Int> {
    val (scope, rows) = queensBoard(4)
    scope.diagonalGlobals(rows)
    val result = solveAuto(scope.build())
    println("${result.status} by ${result.descriptor.id}")
    check(result.status == SolveStatus.FEASIBLE) { result.toString() }
    val assignment = checkNotNull(result.assignment)
    return rows.map { checkNotNull(assignment.intValueOf(it)) }
}
// --8<-- [end:solve]

// --8<-- [start:enumerate]
internal suspend fun enumerateQueens(
    size: Int,
    pairwise: Boolean = false,
    symmetry: Boolean = false,
): List<List<Int>> {
    val (scope, rows) = queensBoard(size)
    if (pairwise) scope.pairwiseDiagonals(rows) else scope.diagonalGlobals(rows)
    if (symmetry) scope.breakRowReflection(rows)
    val model = scope.build()
    val boards = mutableListOf<List<Int>>()
    DefaultBackends.load().use { loaded ->
        val backend = loaded.registry.select(model, requiredCapabilities = setOf(SolverCapability.ENUMERATION))
        backend.create().use { solver ->
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
    return boards
}
// --8<-- [end:enumerate]
