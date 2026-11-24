package io.github.gciatto.ktconstraints.solve.mip

import com.google.ortools.Loader
import com.google.ortools.linearsolver.MPSolver
import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.solve.ConstraintSolver
import io.github.gciatto.ktconstraints.solve.SolveOptions
import io.github.gciatto.ktconstraints.solve.SolverCapability
import io.github.gciatto.ktconstraints.solve.SolverDescriptor
import io.github.gciatto.ktconstraints.solve.mip.ortools.OrToolsMipSolver
import io.github.gciatto.ktconstraints.solve.mip.program.MipLowering
import io.github.gciatto.ktconstraints.solve.spi.BackendAvailability
import io.github.gciatto.ktconstraints.solve.spi.SolverBackend
import io.github.gciatto.ktconstraints.solve.spi.exception.BackendUnavailableException
import io.github.gciatto.ktconstraints.solve.spi.support.SupportReport

/**
 * A MIP engine bundled with OR-Tools, reached through its `MPSolver` wrapper. These routes are distinct from both the
 * MiniZinc routes and a direct C API route.
 */
class OrToolsMipBackend private constructor(
    override val descriptor: SolverDescriptor,
    private val engine: String,
    /** Per-solve time limit imposed by the backend, or null when close and cancellation can interrupt the engine. */
    val solveLimitMillis: Long?,
) : SolverBackend {
    @Suppress("TooGenericExceptionCaught") // The loader reports extraction failures as plain runtime exceptions.
    override fun availability(): BackendAvailability =
        try {
            Loader.loadNativeLibraries()
            val probe = MPSolver.createSolver(engine)
            when {
                probe == null -> BackendAvailability(false, "OR-Tools was built without $engine")
                probe.solve() == MPSolver.ResultStatus.OPTIMAL -> BackendAvailability(true).also { probe.delete() }
                else -> BackendAvailability(false, "$engine failed its startup check").also { probe.delete() }
            }
        } catch (problem: LinkageError) {
            BackendAvailability(false, "OR-Tools native library cannot be loaded: ${problem.message}")
        } catch (problem: RuntimeException) {
            BackendAvailability(false, "OR-Tools native library cannot be extracted: ${problem.message}")
        }

    override fun support(
        model: ConstraintModel,
        options: SolveOptions,
    ): SupportReport = MipLowering.report(model)

    override fun createSolver(): ConstraintSolver {
        val probe = availability()
        if (!probe.available) throw BackendUnavailableException("${descriptor.id}: ${probe.reason}")
        return OrToolsMipSolver(descriptor, engine, solveLimitMillis)
    }

    /** Factories and route identities. */
    companion object {
        /** `scip/or-tools`: interruptible SCIP. */
        @JvmField
        val SCIP: SolverDescriptor = descriptor("scip")

        /** `highs/or-tools`: HiGHS, which OR-Tools cannot interrupt. */
        @JvmField
        val HIGHS: SolverDescriptor = descriptor("highs")

        /** SCIP through OR-Tools; close and cancellation interrupt an in-flight solve. */
        @JvmStatic
        fun scip(): OrToolsMipBackend = OrToolsMipBackend(SCIP, "SCIP", null)

        /**
         * HiGHS through OR-Tools. OR-Tools cannot interrupt HiGHS, so every solve is bounded by [solveLimitMillis]:
         * close returns at once and an abandoned solve ends at that limit.
         */
        @JvmStatic
        fun highs(solveLimitMillis: Long): OrToolsMipBackend {
            require(solveLimitMillis > 0) { "HiGHS needs a positive solve limit: $solveLimitMillis" }
            return OrToolsMipBackend(HIGHS, "HIGHS", solveLimitMillis)
        }

        private fun descriptor(engine: String) =
            SolverDescriptor(
                engine,
                "or-tools",
                setOf(
                    SolverCapability.SATISFACTION,
                    SolverCapability.OPTIMIZATION,
                    SolverCapability.INTEGER_ARITHMETIC,
                    SolverCapability.GLOBAL_CONSTRAINTS,
                ),
            )
    }
}
