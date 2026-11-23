package io.github.gciatto.ktconstraints.solve.kotlin

import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.solve.SolveOptions
import io.github.gciatto.ktconstraints.solve.SolverCapability
import io.github.gciatto.ktconstraints.solve.SolverDescriptor
import io.github.gciatto.ktconstraints.solve.incremental.IncrementalConstraintSolver
import io.github.gciatto.ktconstraints.solve.kotlin.runtime.KotlinSolver
import io.github.gciatto.ktconstraints.solve.spi.BackendAvailability
import io.github.gciatto.ktconstraints.solve.spi.SolverBackend
import io.github.gciatto.ktconstraints.solve.spi.support.SupportReport

/** Pure Kotlin backend: exact finite discrete circuits, clause learning and rational linear feasibility. */
class KotlinBackend(
    private val configuration: KotlinConfiguration = KotlinConfiguration(),
) : SolverBackend {
    override val descriptor: SolverDescriptor =
        SolverDescriptor(
            "kotlin",
            "direct",
            setOf(
                SolverCapability.SATISFACTION,
                SolverCapability.OPTIMIZATION,
                SolverCapability.ENUMERATION,
                SolverCapability.INCREMENTAL_ASSERTIONS,
                SolverCapability.BACKTRACKING,
                SolverCapability.ASSUMPTIONS,
                SolverCapability.INTEGER_ARITHMETIC,
                SolverCapability.REAL_ARITHMETIC,
                SolverCapability.BITVECTORS,
                SolverCapability.GLOBAL_CONSTRAINTS,
                SolverCapability.UNSAT_CORES,
            ),
        )

    override fun availability(): BackendAvailability = BackendAvailability(true)

    override fun support(
        model: ConstraintModel,
        options: SolveOptions,
    ): SupportReport = KotlinSupport.report(model)

    override fun create(): IncrementalConstraintSolver = KotlinSolver(descriptor, configuration)
}
