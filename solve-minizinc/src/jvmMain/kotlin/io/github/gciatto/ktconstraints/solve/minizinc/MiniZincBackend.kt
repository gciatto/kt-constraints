package io.github.gciatto.ktconstraints.solve.minizinc

import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.solve.ConstraintSolver
import io.github.gciatto.ktconstraints.solve.SolveOptions
import io.github.gciatto.ktconstraints.solve.SolverDescriptor
import io.github.gciatto.ktconstraints.solve.minizinc.runtime.MiniZincSolver
import io.github.gciatto.ktconstraints.solve.minizinc.runtime.probeMiniZinc
import io.github.gciatto.ktconstraints.solve.spi.BackendAvailability
import io.github.gciatto.ktconstraints.solve.spi.SolverBackend
import io.github.gciatto.ktconstraints.solve.spi.exception.BackendUnavailableException
import io.github.gciatto.ktconstraints.solve.spi.support.SupportReport

/** JVM execution of one explicitly selected MiniZinc engine configuration. */
class MiniZincBackend(
    private val engine: MiniZincEngine,
    private val executable: String = "minizinc",
) : SolverBackend {
    override val descriptor: SolverDescriptor = engine.descriptor
    private val compiler = MiniZincCompiler(engine)

    override fun availability(): BackendAvailability = probeMiniZinc(executable, engine)

    override fun support(
        model: ConstraintModel,
        options: SolveOptions,
    ): SupportReport = compiler.support(model, options)

    override fun createSolver(): ConstraintSolver {
        val probe = availability()
        if (!probe.available) throw BackendUnavailableException("${descriptor.id}: ${probe.reason}")
        return MiniZincSolver(executable, engine)
    }
}
