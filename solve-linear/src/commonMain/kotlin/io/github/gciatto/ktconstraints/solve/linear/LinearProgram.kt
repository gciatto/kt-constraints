package io.github.gciatto.ktconstraints.solve.linear

import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.solve.spi.exception.UnsupportedModelException
import io.github.gciatto.ktconstraints.solve.spi.support.SupportReport

/**
 * An exact linear or mixed-integer linear program: one column per model variable, in declaration order, rows with
 * rational coefficients, and an optional objective. It represents the model exactly: a solution of the program,
 * read back with [LinearSolution], is a solution of the model.
 */
class LinearProgram internal constructor(
    /** Decision columns, one per model variable. */
    val columns: List<LinearColumn>,
    /** Constraint rows. */
    val rows: List<LinearRow>,
    /** Objective, or null for satisfaction. */
    val objective: LinearObjective?,
) {
    /** Whether any column must be integral, making this a mixed-integer program. */
    val hasIntegers: Boolean get() = columns.any { it.integer }

    /** Whether any row is a strict inequality, which floating-point engines cannot represent. */
    val hasStrictRows: Boolean get() = rows.any { it.relation == LinearRelation.LESS }

    /** Translation of models. */
    companion object {
        /** The exact program of [model]; fails with every unsupported construct when it is not linear. */
        fun of(model: ConstraintModel): LinearProgram {
            val translation = LinearTranslation(model)
            if (translation.unsupported.isNotEmpty()) throw UnsupportedModelException(SupportReport(translation.unsupported))
            return translation.program()
        }

        /** Whether [model] is a linear program, with every construct that is not. */
        fun report(model: ConstraintModel): SupportReport = SupportReport(LinearTranslation(model).unsupported)
    }
}
