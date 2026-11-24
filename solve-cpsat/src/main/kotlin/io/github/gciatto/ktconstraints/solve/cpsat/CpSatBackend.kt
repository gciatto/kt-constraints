package io.github.gciatto.ktconstraints.solve.cpsat

import com.google.ortools.Loader
import com.google.ortools.sat.CpModel
import com.google.ortools.sat.CpSolver
import com.google.ortools.sat.CpSolverStatus
import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.solve.ConstraintSolver
import io.github.gciatto.ktconstraints.solve.SolveOptions
import io.github.gciatto.ktconstraints.solve.SolverCapability
import io.github.gciatto.ktconstraints.solve.SolverDescriptor
import io.github.gciatto.ktconstraints.solve.cpsat.runtime.CpSatSolver
import io.github.gciatto.ktconstraints.solve.cpsat.translation.CpSatSupport
import io.github.gciatto.ktconstraints.solve.spi.BackendAvailability
import io.github.gciatto.ktconstraints.solve.spi.SolverBackend
import io.github.gciatto.ktconstraints.solve.spi.exception.BackendUnavailableException
import io.github.gciatto.ktconstraints.solve.spi.support.SupportReport

/** Direct JVM CP-SAT route through the official OR-Tools Java API; distinct from `cp-sat/minizinc`. */
class CpSatBackend
    @JvmOverloads
    constructor(
        /** Search workers for optimization, or 0 for CP-SAT's default; satisfaction enumeration always uses one. */
        val workers: Int = 0,
        /** Fixed random seed, or null for CP-SAT's default. */
        val seed: Int? = null,
    ) : SolverBackend {
        init {
            require(workers >= 0) { "Worker count must be non-negative: $workers" }
        }

        override val descriptor: SolverDescriptor = DESCRIPTOR

        @Suppress("TooGenericExceptionCaught") // The loader reports extraction failures as plain runtime exceptions.
        override fun availability(): BackendAvailability =
            try {
                Loader.loadNativeLibraries()
                val probe = CpModel().apply { addBoolOr(listOf(newBoolVar("probe"))) }
                if (CpSolver().solve(probe) == CpSolverStatus.OPTIMAL) {
                    BackendAvailability(true)
                } else {
                    BackendAvailability(false, "CP-SAT failed its startup check")
                }
            } catch (problem: LinkageError) {
                BackendAvailability(false, "OR-Tools native library cannot be loaded: ${problem.message}")
            } catch (problem: RuntimeException) {
                BackendAvailability(false, "OR-Tools native library cannot be extracted: ${problem.message}")
            }

        override fun support(
            model: ConstraintModel,
            options: SolveOptions,
        ): SupportReport = CpSatSupport.report(model)

        override fun createSolver(): ConstraintSolver {
            val probe = availability()
            if (!probe.available) throw BackendUnavailableException("${descriptor.id}: ${probe.reason}")
            return CpSatSolver(descriptor, workers, seed)
        }

        /** Route identity shared by every CP-SAT backend instance. */
        companion object {
            /** `cp-sat/direct`: batch satisfaction, enumeration and optimization over Booleans and int64 integers. */
            @JvmField
            val DESCRIPTOR: SolverDescriptor =
                SolverDescriptor(
                    "cp-sat",
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
