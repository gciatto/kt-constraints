package io.github.gciatto.ktconstraints.solve.linear

import io.github.gciatto.ktconstraints.core.evaluation.GroundEvaluator
import io.github.gciatto.ktconstraints.core.integer.IntExpr
import io.github.gciatto.ktconstraints.core.real.RealExpr
import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.model.isReal
import io.github.gciatto.ktconstraints.solve.SolveOptions
import io.github.gciatto.ktconstraints.solve.assignment.project
import io.github.gciatto.ktconstraints.solve.spi.exception.BackendExecutionException
import io.github.gciatto.ktconstraints.solve.spi.exception.UnsupportedModelException
import io.github.gciatto.ktconstraints.solve.spi.feature.ModelFeatures
import io.github.gciatto.ktconstraints.solve.spi.support.SupportReport
import io.github.gciatto.ktconstraints.solve.stream.SolutionFound

/**
 * The contract of routes that solve [LinearProgram]s in floating point. Models with reals need the caller's
 * [SolveOptions.numericTolerance] and no strict inequalities, which floating point cannot represent. Every answer is
 * read back exactly and accepted only within that tolerance; integer-only programs must hold exactly.
 */
object FloatingPointPrograms {
    /** Rejection of a real model without a tolerance. */
    const val TOLERANCE_NEEDED: String = "Floating-point reals need SolveOptions.numericTolerance"

    /** Rejection of a strict real inequality. */
    const val STRICT: String = "Strict real inequality: floating-point engines cannot represent it"

    private const val DEFAULT_INTEGRALITY = 1e-6

    /** Whether [model] uses reals, so that a floating-point route needs the caller's consent. */
    fun usesReals(model: ConstraintModel): Boolean = ModelFeatures.of(model).usesReals

    /** Whether a floating-point route can solve [model] under [options]. */
    fun report(
        model: ConstraintModel,
        options: SolveOptions,
    ): SupportReport {
        val linear = LinearProgram.report(model).unsupported
        val strict = linear.isEmpty() && LinearProgram.of(model).hasStrictRows
        val consent = !usesReals(model) || options.numericTolerance != null
        return SupportReport(linear + listOfNotNull(TOLERANCE_NEEDED.takeUnless { consent }, STRICT.takeIf { strict }))
    }

    /** The program of [model]; fails with [report]'s reasons when a floating-point route cannot solve it. */
    fun lower(
        model: ConstraintModel,
        options: SolveOptions,
    ): LinearProgram {
        val report = report(model, options)
        if (!report.isSupported) throw UnsupportedModelException(report)
        return LinearProgram.of(model)
    }

    /**
     * Reads a floating-point answer of [route] exactly. Integer columns are rounded within the integrality tolerance,
     * and the answer is rejected when a bound or row is exceeded beyond the feasibility tolerance (zero without a
     * tolerance, for integer-only programs).
     */
    fun accept(
        program: LinearProgram,
        values: List<Double>,
        options: SolveOptions,
        route: String,
    ): LinearSolution {
        val tolerance = options.numericTolerance
        val solution =
            try {
                LinearSolution.ofDoubles(program, values, tolerance?.integrality ?: DEFAULT_INTEGRALITY)
            } catch (problem: IllegalStateException) {
                throw BackendExecutionException("$route: ${problem.message}", problem)
            }
        val limit = exactRational(tolerance?.feasibility ?: 0.0)
        if (solution.violation > limit) {
            throw BackendExecutionException("$route exceeded a constraint by ${solution.violation}, beyond $limit")
        }
        return solution
    }

    /** [model]'s incumbent for an accepted [solution], with its objective computed exactly. */
    fun found(
        model: ConstraintModel,
        solution: LinearSolution,
    ): SolutionFound {
        val full = solution.assignment()
        val projected = full.project(model.outputVariables)
        val goal = model.objective ?: return SolutionFound(projected)
        return if (goal.isReal) {
            SolutionFound(projected, realObjectiveValue = GroundEvaluator.evaluate(goal.expression as RealExpr, full))
        } else {
            SolutionFound(projected, GroundEvaluator.evaluate(goal.expression as IntExpr, full))
        }
    }
}
