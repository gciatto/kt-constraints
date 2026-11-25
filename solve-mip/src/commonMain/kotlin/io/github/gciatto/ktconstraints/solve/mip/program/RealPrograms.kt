package io.github.gciatto.ktconstraints.solve.mip.program

import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.solve.SolveOptions
import io.github.gciatto.ktconstraints.solve.linear.LinearProgram
import io.github.gciatto.ktconstraints.solve.spi.exception.UnsupportedModelException
import io.github.gciatto.ktconstraints.solve.spi.feature.ModelFeatures
import io.github.gciatto.ktconstraints.solve.spi.support.SupportReport

/**
 * Models with reals reach MIP engines as linear programs with continuous columns, solved in floating point: only
 * with the caller's numeric tolerance, and without strict inequalities, which floating point cannot represent.
 */
internal object RealPrograms {
    const val TOLERANCE_NEEDED = "Floating-point reals need SolveOptions.numericTolerance"
    const val STRICT = "Strict real inequality: floating-point engines cannot represent it"

    fun isReal(model: ConstraintModel): Boolean = ModelFeatures.of(model).usesReals

    fun report(
        model: ConstraintModel,
        options: SolveOptions,
    ): SupportReport {
        val linear = LinearProgram.report(model).unsupported
        val strict = linear.isEmpty() && LinearProgram.of(model).hasStrictRows
        return SupportReport(linear + listOfNotNull(TOLERANCE_NEEDED.takeIf { options.numericTolerance == null }, STRICT.takeIf { strict }))
    }

    fun lower(
        model: ConstraintModel,
        options: SolveOptions,
    ): LinearProgram {
        val report = report(model, options)
        if (!report.isSupported) throw UnsupportedModelException(report)
        return LinearProgram.of(model)
    }
}
