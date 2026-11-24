package io.github.gciatto.ktconstraints.solve.spi

import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.solve.ConstraintSolver
import io.github.gciatto.ktconstraints.solve.SolveOptions
import io.github.gciatto.ktconstraints.solve.SolverDescriptor
import io.github.gciatto.ktconstraints.solve.incremental.IncrementalConstraintSolver
import io.github.gciatto.ktconstraints.solve.spi.support.SupportReport

/**
 * Provider with separate runtime, capability and whole-model support declarations.
 * Closing a backend releases its runtime; backends without one keep the default no-op [close].
 */
interface SolverBackend : AutoCloseable {
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
    fun createSolver(): ConstraintSolver

    /** Releases the backend runtime; solvers created earlier must not be used afterwards. */
    override fun close() = Unit
}

/** Creates a solver, runs [block] on it and closes it, also when [block] fails. */
inline fun <R> SolverBackend.withSolver(block: (ConstraintSolver) -> R): R = createSolver().use(block)

/** Creates a solver that opens incremental sessions, or throws [UnsupportedOperationException] when the route has none. */
fun SolverBackend.createIncrementalSolver(): IncrementalConstraintSolver {
    val solver = createSolver()
    if (solver is IncrementalConstraintSolver) return solver
    solver.close()
    throw UnsupportedOperationException("${descriptor.id} does not support incremental sessions")
}
