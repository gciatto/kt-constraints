package io.github.gciatto.ktconstraints.solve.minizinc

import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.solve.SolveOptions
import io.github.gciatto.ktconstraints.solve.SolverDescriptor
import io.github.gciatto.ktconstraints.solve.spi.ManagedConstraintSolver
import io.github.gciatto.ktconstraints.solve.spi.ManagedSolutionStream

internal class MiniZincSolver(private val executable: String) : ManagedConstraintSolver() {
    override val descriptor: SolverDescriptor = gecodeDescriptor

    override fun openStream(model: ConstraintModel, options: SolveOptions, onClosed: () -> Unit): ManagedSolutionStream =
        MiniZincStream(executable, MiniZincCompiler().compile(model, options), options, onClosed)
}
