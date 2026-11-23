package io.github.gciatto.ktconstraints.solve.choco.runtime
import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.solve.SolveOptions
import io.github.gciatto.ktconstraints.solve.SolverDescriptor
import io.github.gciatto.ktconstraints.solve.spi.managed.ManagedConstraintSolver
import io.github.gciatto.ktconstraints.solve.spi.managed.ManagedSolutionStream

/** Batch Choco solver; every stream builds and owns its own Choco model. */
internal class ChocoSolver(
    override val descriptor: SolverDescriptor,
) : ManagedConstraintSolver() {
    override fun openStream(
        model: ConstraintModel,
        options: SolveOptions,
        onClosed: () -> Unit,
    ): ManagedSolutionStream = ChocoStream(model, options, descriptor, onClosed)
}
