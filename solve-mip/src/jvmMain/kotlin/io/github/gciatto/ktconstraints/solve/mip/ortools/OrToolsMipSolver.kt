package io.github.gciatto.ktconstraints.solve.mip.ortools
import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.solve.SolveOptions
import io.github.gciatto.ktconstraints.solve.SolverDescriptor
import io.github.gciatto.ktconstraints.solve.spi.managed.ManagedConstraintSolver
import io.github.gciatto.ktconstraints.solve.spi.managed.ManagedSolutionStream

/** Batch MIP solver; every stream lowers its own program and owns its own `MPSolver`. */
internal class OrToolsMipSolver(
    override val descriptor: SolverDescriptor,
    private val engine: String,
    private val solveLimitMillis: Long?,
) : ManagedConstraintSolver() {
    override fun openStream(
        model: ConstraintModel,
        options: SolveOptions,
        onClosed: () -> Unit,
    ): ManagedSolutionStream = OrToolsMipStream(model, options, descriptor, engine, solveLimitMillis, onClosed)
}
