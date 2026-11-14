package io.github.gciatto.ktconstraints.solve.minizinc

import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.solve.ConstraintSolver
import io.github.gciatto.ktconstraints.solve.SolveOptions
import io.github.gciatto.ktconstraints.solve.SolverDescriptor
import io.github.gciatto.ktconstraints.solve.spi.BackendAvailability
import io.github.gciatto.ktconstraints.solve.spi.BackendUnavailableException
import io.github.gciatto.ktconstraints.solve.spi.SolverBackend
import io.github.gciatto.ktconstraints.solve.spi.SupportReport

/** JVM Gecode execution through an installed MiniZinc CLI; other engines are separate routes. */
class MiniZincGecodeBackend(
    private val executable: String = "minizinc",
) : SolverBackend {
    override val descriptor: SolverDescriptor = gecodeDescriptor
    private val compiler = MiniZincCompiler()

    override fun availability(): BackendAvailability = probeMiniZinc(executable)

    override fun support(
        model: ConstraintModel,
        options: SolveOptions,
    ): SupportReport = compiler.support(model, options)

    override fun create(): ConstraintSolver {
        val probe = availability()
        if (!probe.available) throw BackendUnavailableException("${descriptor.id}: ${probe.reason}")
        return MiniZincSolver(executable)
    }
}
