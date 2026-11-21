package io.github.gciatto.ktconstraints.solve.portfolio

import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.solve.ConstraintSolver
import io.github.gciatto.ktconstraints.solve.SolveOptions
import io.github.gciatto.ktconstraints.solve.SolveResult
import io.github.gciatto.ktconstraints.solve.SolverCapability
import io.github.gciatto.ktconstraints.solve.SolverDescriptor
import io.github.gciatto.ktconstraints.solve.spi.BackendAvailability
import io.github.gciatto.ktconstraints.solve.spi.SolverBackend
import io.github.gciatto.ktconstraints.solve.spi.SupportReport

/** A backend whose solvers wait [delayMillis] (capped by the solve timeout) and then run [script]. */
internal class ScriptedBackend(
    private val name: String,
    private val delayMillis: Long,
    private val log: PortfolioLog,
    private val available: Boolean = true,
    private val script: (ConstraintModel, SolverDescriptor, Boolean) -> SolveResult,
) : SolverBackend {
    override val descriptor =
        SolverDescriptor(
            name,
            "direct",
            setOf(
                SolverCapability.SATISFACTION,
                SolverCapability.OPTIMIZATION,
                SolverCapability.INTEGER_ARITHMETIC,
                SolverCapability.GLOBAL_CONSTRAINTS,
            ),
        )

    override fun availability() = if (available) BackendAvailability(true) else BackendAvailability(false, "$name is missing")

    override fun support(
        model: ConstraintModel,
        options: SolveOptions,
    ) = SupportReport()

    override fun create(): ConstraintSolver = ScriptedSolver(descriptor, delayMillis, log, script).also { log.created += descriptor.engine }
}
