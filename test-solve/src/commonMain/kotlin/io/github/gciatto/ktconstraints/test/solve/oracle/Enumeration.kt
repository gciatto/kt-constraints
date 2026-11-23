package io.github.gciatto.ktconstraints.test.solve.oracle
import io.github.gciatto.ktconstraints.core.Variable
import io.github.gciatto.ktconstraints.core.bool.BoolVar
import io.github.gciatto.ktconstraints.core.integer.IntVar
import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.solve.ConstraintSolver
import io.github.gciatto.ktconstraints.solve.SolveOptions
import io.github.gciatto.ktconstraints.solve.assignment.Assignment
import io.github.gciatto.ktconstraints.solve.result.SolveResult
import io.github.gciatto.ktconstraints.solve.stream.SolutionFound
import io.github.gciatto.ktconstraints.solve.stream.SolveCompleted

/** Collects projected solution tuples (or, with [objectives], objective values) and the terminal result. */
@Suppress("RedundantSuspendModifier") // detekt misses the suspending SolutionStream.next calls.
suspend fun enumerate(
    solver: ConstraintSolver,
    model: ConstraintModel,
    options: SolveOptions = SolveOptions.DEFAULT,
    objectives: Boolean = false,
): Pair<List<List<Any?>>, SolveResult> {
    val found = mutableListOf<List<Any?>>()
    solver.solutions(model, options).use { stream ->
        var event = stream.next()
        while (event is SolutionFound) {
            found += if (objectives) listOf(event.objectiveValue) else event.assignment.tuple(model.outputVariables)
            event = stream.next()
        }
        return found to (event as SolveCompleted).result
    }
}

private fun Assignment.tuple(variables: List<Variable>): List<Any?> =
    variables.map {
        when (it) {
            is BoolVar -> valueOf(it)
            is IntVar -> valueOf(it)
            else -> error("Unexpected output variable ${it.name}")
        }
    }
