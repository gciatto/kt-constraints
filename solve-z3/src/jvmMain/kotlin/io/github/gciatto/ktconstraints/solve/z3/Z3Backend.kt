package io.github.gciatto.ktconstraints.solve.z3

import com.microsoft.z3.Context
import com.microsoft.z3.Status
import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.solve.SolveOptions
import io.github.gciatto.ktconstraints.solve.SolverCapability
import io.github.gciatto.ktconstraints.solve.SolverDescriptor
import io.github.gciatto.ktconstraints.solve.incremental.IncrementalConstraintSolver
import io.github.gciatto.ktconstraints.solve.spi.BackendAvailability
import io.github.gciatto.ktconstraints.solve.spi.SolverBackend
import io.github.gciatto.ktconstraints.solve.spi.exception.BackendUnavailableException
import io.github.gciatto.ktconstraints.solve.spi.support.SupportReport
import io.github.gciatto.ktconstraints.solve.z3.port.JavaZ3Owner
import io.github.gciatto.ktconstraints.solve.z3.session.Z3Solver
import io.github.gciatto.ktconstraints.solve.z3.translation.Z3Support

/** Direct JVM Z3 route through the bundled official Java API; distinct from any MiniZinc route. */
class Z3Backend : SolverBackend {
    override val descriptor: SolverDescriptor = DESCRIPTOR

    override fun availability(): BackendAvailability =
        try {
            Context().use { context ->
                if (context.mkSolver().check() == Status.SATISFIABLE) {
                    BackendAvailability(true)
                } else {
                    BackendAvailability(false, "Z3 failed its startup check")
                }
            }
        } catch (problem: LinkageError) {
            BackendAvailability(false, "Z3 native library cannot be loaded: ${problem.message}")
        }

    override fun support(
        model: ConstraintModel,
        options: SolveOptions,
    ): SupportReport = Z3Support.report(model)

    override fun createSolver(): IncrementalConstraintSolver {
        val probe = availability()
        if (!probe.available) throw BackendUnavailableException("${descriptor.id}: ${probe.reason}")
        return Z3Solver(descriptor) { JavaZ3Owner() }
    }

    /** Route identity shared by every JVM Z3 backend instance. */
    companion object {
        /** `z3/direct`: batch solving plus incremental sessions with checkpoints, assumptions and unsat cores. */
        @JvmField
        val DESCRIPTOR: SolverDescriptor =
            SolverDescriptor(
                "z3",
                "direct",
                setOf(
                    SolverCapability.SATISFACTION,
                    SolverCapability.OPTIMIZATION,
                    SolverCapability.ENUMERATION,
                    SolverCapability.INTEGER_ARITHMETIC,
                    SolverCapability.GLOBAL_CONSTRAINTS,
                    SolverCapability.INCREMENTAL_ASSERTIONS,
                    SolverCapability.INCREMENTAL_OPTIMIZATION,
                    SolverCapability.BACKTRACKING,
                    SolverCapability.ASSUMPTIONS,
                    SolverCapability.UNSAT_CORES,
                    SolverCapability.REAL_ARITHMETIC,
                    SolverCapability.BITVECTORS,
                ),
            )
    }
}
