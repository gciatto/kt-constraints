package io.github.gciatto.ktconstraints.solve.spi

import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.solve.ConstraintSolver
import io.github.gciatto.ktconstraints.solve.SolveOptions
import io.github.gciatto.ktconstraints.solve.SolverDescriptor
import io.github.gciatto.ktconstraints.solve.spi.support.SupportReport

internal class FakeBackend(
    override val descriptor: SolverDescriptor,
    private val availabilityReport: BackendAvailability = BackendAvailability(true),
    private val supportReport: SupportReport = SupportReport(),
) : SolverBackend {
    override fun availability(): BackendAvailability = availabilityReport

    override fun support(
        model: ConstraintModel,
        options: SolveOptions,
    ): SupportReport = supportReport

    override fun createSolver(): ConstraintSolver = error("Registry selection must not create solvers")
}
