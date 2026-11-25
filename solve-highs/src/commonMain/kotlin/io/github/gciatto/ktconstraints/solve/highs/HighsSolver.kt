package io.github.gciatto.ktconstraints.solve.highs

import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.solve.SolveOptions
import io.github.gciatto.ktconstraints.solve.SolverDescriptor
import io.github.gciatto.ktconstraints.solve.linear.FloatingPointPrograms
import io.github.gciatto.ktconstraints.solve.spi.managed.ManagedConstraintSolver
import io.github.gciatto.ktconstraints.solve.spi.managed.ManagedSolutionStream

/** Solves linear programs with HiGHS, one fresh [HighsRun] per solve. */
internal class HighsSolver(
    override val descriptor: SolverDescriptor,
    private val runs: () -> HighsRun,
) : ManagedConstraintSolver() {
    override fun openStream(
        model: ConstraintModel,
        options: SolveOptions,
        onClosed: () -> Unit,
    ): ManagedSolutionStream = HighsStream(model, FloatingPointPrograms.lower(model, options), options, descriptor, runs(), onClosed)
}
