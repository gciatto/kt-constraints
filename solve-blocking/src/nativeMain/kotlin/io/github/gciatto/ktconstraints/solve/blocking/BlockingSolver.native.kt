package io.github.gciatto.ktconstraints.solve.blocking

import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.solve.ConstraintSolver
import io.github.gciatto.ktconstraints.solve.SolveOptions
import io.github.gciatto.ktconstraints.solve.SolverDescriptor
import io.github.gciatto.ktconstraints.solve.result.SolveResult
import io.github.gciatto.ktconstraints.solve.spi.SolverBackend
import kotlinx.coroutines.runBlocking

/**
 * Owner of one [ConstraintSolver] with blocking and iterator entry points, so callers never handle Kotlin suspension.
 * Closing it closes the solver and its streams, but never a [SolverBackend] it was created from.
 */
actual class BlockingSolver private constructor(
    private val solver: ConstraintSolver,
) : AutoCloseable {
    actual val descriptor: SolverDescriptor get() = solver.descriptor

    actual fun solve(
        model: ConstraintModel,
        options: SolveOptions,
    ): SolveResult = runBlocking { solver.solve(model, options) }

    actual fun solutions(
        model: ConstraintModel,
        options: SolveOptions,
    ): BlockingSolutionStream = BlockingSolutionStream(solver.solutions(model, options))

    actual fun openSession(model: ConstraintModel): BlockingSession = BlockingSession(solver.openIncrementalSession(model))

    actual override fun close() = solver.close()

    /** Static factories. */
    actual companion object {
        actual fun of(backend: SolverBackend): BlockingSolver = of(backend.createSolver())

        actual fun of(solver: ConstraintSolver): BlockingSolver = BlockingSolver(solver)
    }
}
