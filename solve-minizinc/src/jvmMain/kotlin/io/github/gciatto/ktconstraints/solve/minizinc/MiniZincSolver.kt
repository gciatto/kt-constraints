package io.github.gciatto.ktconstraints.solve.minizinc

import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.solve.SolveOptions
import io.github.gciatto.ktconstraints.solve.SolverDescriptor
import io.github.gciatto.ktconstraints.solve.spi.ManagedConstraintSolver
import io.github.gciatto.ktconstraints.solve.spi.ManagedSolutionStream

internal class MiniZincSolver(
    private val executable: String,
    private val engine: MiniZincEngine = MiniZincEngine.GECODE,
) : ManagedConstraintSolver() {
    override val descriptor: SolverDescriptor = engine.descriptor

    override fun openStream(
        model: ConstraintModel,
        options: SolveOptions,
        onClosed: () -> Unit,
    ): ManagedSolutionStream = MiniZincStream(executable, MiniZincCompiler(engine).compile(model, options), options, onClosed)
}
