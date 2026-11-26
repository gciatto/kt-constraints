@file:OptIn(ExperimentalForeignApi::class)

package io.github.gciatto.ktconstraints.solve.z3

import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.solve.SolveOptions
import io.github.gciatto.ktconstraints.solve.SolverCapability
import io.github.gciatto.ktconstraints.solve.SolverDescriptor
import io.github.gciatto.ktconstraints.solve.incremental.CheckStatus
import io.github.gciatto.ktconstraints.solve.incremental.IncrementalConstraintSolver
import io.github.gciatto.ktconstraints.solve.spi.BackendAvailability
import io.github.gciatto.ktconstraints.solve.spi.SolverBackend
import io.github.gciatto.ktconstraints.solve.spi.exception.BackendUnavailableException
import io.github.gciatto.ktconstraints.solve.spi.support.SupportReport
import io.github.gciatto.ktconstraints.solve.z3.port.NativeZ3Library
import io.github.gciatto.ktconstraints.solve.z3.port.NativeZ3Owner
import io.github.gciatto.ktconstraints.solve.z3.session.Z3Solver
import io.github.gciatto.ktconstraints.solve.z3.translation.Z3Support
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.runBlocking

/**
 * Direct Kotlin/Native Z3 route through the C API, loading the library at [libraryPath] at run time.
 * The default path is the `KT_CONSTRAINTS_Z3_LIBRARY` environment variable, else the platform's library name.
 */
class Z3NativeBackend(
    private val libraryPath: String = NativeZ3Library.defaultPath,
) : SolverBackend {
    override val descriptor: SolverDescriptor = DESCRIPTOR

    override fun availability(): BackendAvailability {
        NativeZ3Library.load(libraryPath)?.let { return BackendAvailability(false, it) }
        return try {
            NativeZ3Owner().use { owner ->
                val outcome = runBlocking { owner.confined { it.check(emptyList(), null) } }
                if (outcome.status == CheckStatus.SATISFIABLE) {
                    BackendAvailability(true)
                } else {
                    BackendAvailability(false, "Z3 ${NativeZ3Library.version()} failed its startup check")
                }
            }
        } catch (problem: IllegalStateException) {
            BackendAvailability(false, "Z3 ${NativeZ3Library.version()} failed its startup check: ${problem.message}")
        }
    }

    override fun support(
        model: ConstraintModel,
        options: SolveOptions,
    ): SupportReport = Z3Support.report(model)

    override fun createSolver(): IncrementalConstraintSolver {
        val probe = availability()
        if (!probe.available) throw BackendUnavailableException("${descriptor.id}: ${probe.reason}")
        return Z3Solver(descriptor) { NativeZ3Owner() }
    }

    /** Route identity shared by every native Z3 backend instance. */
    companion object {
        /** `z3/native`: batch solving plus incremental sessions with checkpoints, assumptions and unsat cores. */
        val DESCRIPTOR: SolverDescriptor =
            SolverDescriptor(
                "z3",
                "native",
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
