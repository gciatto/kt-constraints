package io.github.gciatto.ktconstraints.solve.highs

import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.solve.ConstraintSolver
import io.github.gciatto.ktconstraints.solve.SolveOptions
import io.github.gciatto.ktconstraints.solve.SolverDescriptor
import io.github.gciatto.ktconstraints.solve.linear.FloatingPointPrograms
import io.github.gciatto.ktconstraints.solve.spi.BackendAvailability
import io.github.gciatto.ktconstraints.solve.spi.SolverBackend
import io.github.gciatto.ktconstraints.solve.spi.exception.BackendUnavailableException
import io.github.gciatto.ktconstraints.solve.spi.support.SupportReport

/**
 * Direct Kotlin/Native HiGHS route through its C API, loading the library at [libraryPath] at run time. The default
 * path is the `KT_CONSTRAINTS_HIGHS_LIBRARY` environment variable, else the platform's library name. It solves linear
 * and mixed-integer linear programs in floating point: models with reals need `SolveOptions.numericTolerance`.
 */
class HighsNativeBackend(
    private val libraryPath: String = NativeHighsLibrary.defaultPath,
) : SolverBackend {
    override val descriptor: SolverDescriptor = HighsRoutes.NATIVE

    override fun availability(): BackendAvailability =
        NativeHighsLibrary.load(libraryPath)?.let { BackendAvailability(false, it) } ?: BackendAvailability(true)

    override fun support(
        model: ConstraintModel,
        options: SolveOptions,
    ): SupportReport = FloatingPointPrograms.report(model, options)

    override fun createSolver(): ConstraintSolver {
        val probe = availability()
        if (!probe.available) throw BackendUnavailableException("${descriptor.id}: ${probe.reason}")
        return HighsSolver(descriptor) { NativeHighsRun() }
    }
}
