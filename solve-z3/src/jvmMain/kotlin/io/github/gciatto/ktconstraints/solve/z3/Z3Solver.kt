package io.github.gciatto.ktconstraints.solve.z3

import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.solve.SolveOptions
import io.github.gciatto.ktconstraints.solve.SolverDescriptor
import io.github.gciatto.ktconstraints.solve.spi.ManagedConstraintSolver
import io.github.gciatto.ktconstraints.solve.spi.ManagedSolutionStream
import io.github.gciatto.ktconstraints.solve.spi.UnsupportedModelException

internal class Z3Solver(
    override val descriptor: SolverDescriptor,
) : ManagedConstraintSolver() {
    override fun openStream(
        model: ConstraintModel,
        options: SolveOptions,
        onClosed: () -> Unit,
    ): ManagedSolutionStream {
        val report = Z3Support.report(model)
        if (!report.isSupported) throw UnsupportedModelException(report)
        return Z3Stream(model, options, descriptor, onClosed)
    }
}
