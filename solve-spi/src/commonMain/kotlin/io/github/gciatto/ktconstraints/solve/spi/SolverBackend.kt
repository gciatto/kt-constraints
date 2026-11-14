package io.github.gciatto.ktconstraints.solve.spi

import io.github.gciatto.ktconstraints.solve.ConstraintSolver
import io.github.gciatto.ktconstraints.solve.SolverDescriptor

/** Provider contract for the future capability-aware solver registry. */
interface SolverBackend {
    /** Metadata; declared support must be proven by the backend contract suite. */
    val descriptor: SolverDescriptor

    /** Availability requires the backend runtime/dependencies to be present on this platform. */
    fun isAvailable(): Boolean

    /** Creates a fresh solver instance, or fails explicitly if unavailable. */
    fun create(): ConstraintSolver
}
