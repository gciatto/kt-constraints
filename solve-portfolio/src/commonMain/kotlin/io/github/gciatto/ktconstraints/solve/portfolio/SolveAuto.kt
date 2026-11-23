package io.github.gciatto.ktconstraints.solve.portfolio

import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.solve.SolveOptions
import io.github.gciatto.ktconstraints.solve.result.SolveResult

/** Solves [model] with the bundled routes of this platform: ranked by the default policy, tried as a [Portfolio.fallback]. */
suspend fun solveAuto(
    model: ConstraintModel,
    options: SolveOptions = SolveOptions.DEFAULT,
): SolveResult = DefaultBackends.load().use { Portfolio(it.registry).fallback(model, options) }
