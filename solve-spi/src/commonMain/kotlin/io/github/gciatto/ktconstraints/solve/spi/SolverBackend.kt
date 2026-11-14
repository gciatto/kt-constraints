package io.github.gciatto.ktconstraints.solve.spi

import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.solve.ConstraintSolver
import io.github.gciatto.ktconstraints.solve.SolveOptions
import io.github.gciatto.ktconstraints.solve.SolverDescriptor

/** Provider with separate runtime, capability and whole-model support declarations. */
interface SolverBackend {
    /** Metadata; declared support must be proven by the backend contract suite. */
    val descriptor: SolverDescriptor

    /** Availability requires the backend runtime/dependencies to be present on this platform. */
    fun isAvailable(): Boolean = availability().available

    /** Probes the runtime and explains why it cannot be used when absent. */
    fun availability(): BackendAvailability

    /** Assesses the entire model and options without dropping or approximating unsupported constructs. */
    fun support(
        model: ConstraintModel,
        options: SolveOptions = SolveOptions.DEFAULT,
    ): SupportReport

    /** Creates a fresh solver instance, or fails explicitly if unavailable. */
    fun create(): ConstraintSolver
}
