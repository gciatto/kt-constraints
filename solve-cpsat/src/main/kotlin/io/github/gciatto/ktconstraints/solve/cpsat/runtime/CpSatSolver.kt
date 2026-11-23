package io.github.gciatto.ktconstraints.solve.cpsat.runtime
import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.solve.SolveOptions
import io.github.gciatto.ktconstraints.solve.SolverDescriptor
import io.github.gciatto.ktconstraints.solve.spi.managed.ManagedConstraintSolver
import io.github.gciatto.ktconstraints.solve.spi.managed.ManagedSolutionStream

/** Batch CP-SAT solver; every stream compiles its own model and owns its own search thread. */
internal class CpSatSolver(
    override val descriptor: SolverDescriptor,
    private val workers: Int,
    private val seed: Int?,
) : ManagedConstraintSolver() {
    override fun openStream(
        model: ConstraintModel,
        options: SolveOptions,
        onClosed: () -> Unit,
    ): ManagedSolutionStream = CpSatStream(model, options, descriptor, workers, seed, onClosed)
}
