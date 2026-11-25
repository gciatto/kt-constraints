package io.github.gciatto.ktconstraints.solve.mip.ortools

import com.google.ortools.linearsolver.MPSolver
import io.github.gciatto.ktconstraints.solve.linear.LinearProgram
import io.github.gciatto.ktconstraints.solve.linear.LinearRelation
import io.github.gciatto.ktconstraints.solve.linear.approximate

/** Passes a [LinearProgram] to `MPSolver` in floating point; answers come back through an exact check. */
internal object LinearOrTools {
    fun populate(
        native: MPSolver,
        program: LinearProgram,
    ) {
        val infinity = MPSolver.infinity()
        val variables =
            program.columns.mapIndexed { i, column ->
                native.makeVar(
                    column.lower?.let { approximate(it) } ?: -infinity,
                    column.upper?.let { approximate(it) } ?: infinity,
                    column.integer,
                    "v$i",
                )
            }
        for (row in program.rows) {
            val rhs = approximate(row.rhs)
            check(row.relation != LinearRelation.LESS) { "Strict rows are rejected during support" }
            val constraint = native.makeConstraint(if (row.relation == LinearRelation.EQUAL) rhs else -infinity, rhs)
            row.terms.forEach { (i, c) -> constraint.setCoefficient(variables[i], approximate(c)) }
        }
        program.objective?.let { goal ->
            val objective = native.objective()
            goal.terms.forEach { (i, c) -> objective.setCoefficient(variables[i], approximate(c)) }
            objective.setOffset(approximate(goal.constant))
            if (goal.maximize) objective.setMaximization() else objective.setMinimization()
        }
    }
}
