package io.github.gciatto.ktconstraints.solve.choco

import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.solve.ConstraintSolver
import io.github.gciatto.ktconstraints.solve.SolveOptions
import io.github.gciatto.ktconstraints.solve.SolverCapability
import io.github.gciatto.ktconstraints.solve.SolverDescriptor
import io.github.gciatto.ktconstraints.solve.choco.runtime.ChocoSolver
import io.github.gciatto.ktconstraints.solve.choco.translation.ChocoSupport
import io.github.gciatto.ktconstraints.solve.spi.BackendAvailability
import io.github.gciatto.ktconstraints.solve.spi.SolverBackend
import io.github.gciatto.ktconstraints.solve.spi.exception.BackendUnavailableException
import io.github.gciatto.ktconstraints.solve.spi.support.SupportReport
import org.chocosolver.solver.Model

/** Direct JVM Choco route through the Choco Java API. */
class ChocoBackend : SolverBackend {
    override val descriptor: SolverDescriptor = DESCRIPTOR

    override fun availability(): BackendAvailability =
        try {
            if (Model().apply { boolVar() }.solver.solve()) {
                BackendAvailability(true)
            } else {
                BackendAvailability(false, "Choco failed its startup check")
            }
        } catch (problem: LinkageError) {
            BackendAvailability(false, "Choco cannot be loaded: ${problem.message}")
        }

    override fun support(
        model: ConstraintModel,
        options: SolveOptions,
    ): SupportReport = ChocoSupport.report(model)

    override fun createSolver(): ConstraintSolver {
        val probe = availability()
        if (!probe.available) throw BackendUnavailableException("${descriptor.id}: ${probe.reason}")
        return ChocoSolver(descriptor)
    }

    /** Route identity shared by every Choco backend instance. */
    companion object {
        /** `choco/direct`: batch satisfaction, enumeration and optimization over Booleans and bounded integers. */
        @JvmField
        val DESCRIPTOR: SolverDescriptor =
            SolverDescriptor(
                "choco",
                "direct",
                setOf(
                    SolverCapability.SATISFACTION,
                    SolverCapability.OPTIMIZATION,
                    SolverCapability.ENUMERATION,
                    SolverCapability.INTEGER_ARITHMETIC,
                    SolverCapability.GLOBAL_CONSTRAINTS,
                ),
            )
    }
}
