package io.github.gciatto.ktconstraints.solve.blocking

import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.solve.ConstraintSolver
import io.github.gciatto.ktconstraints.solve.SolveOptions
import io.github.gciatto.ktconstraints.solve.SolverDescriptor
import io.github.gciatto.ktconstraints.solve.incremental.IncrementalConstraintSolver
import io.github.gciatto.ktconstraints.solve.incremental.IncrementalSession
import io.github.gciatto.ktconstraints.solve.result.SolveResult
import io.github.gciatto.ktconstraints.solve.spi.SolverBackend
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

/**
 * Owner of one [ConstraintSolver] with blocking and iterator entry points, so callers never handle Kotlin suspension;
 * on the JVM it also offers `CompletableFuture` variants. Closing it cancels pending work and closes the solver and
 * its streams, but never a [SolverBackend] it was created from.
 */
expect class BlockingSolver : AutoCloseable {
    /** Engine and execution route of the wrapped solver. */
    val descriptor: SolverDescriptor

    /** Solves on the calling thread; on the JVM, interrupting that thread cancels the search. */
    @JvmOverloads
    fun solve(
        model: ConstraintModel,
        options: SolveOptions = SolveOptions.DEFAULT,
    ): SolveResult

    /** Opens a single-pass search to iterate; close it when stopping early. */
    @JvmOverloads
    fun solutions(
        model: ConstraintModel,
        options: SolveOptions = SolveOptions.DEFAULT,
    ): BlockingSolutionStream

    /**
     * Opens an incremental session over [model], owned by this solver. Throws [UnsupportedOperationException] when the
     * route has no incremental sessions, rather than emulating them by re-solving.
     */
    fun openSession(model: ConstraintModel): BlockingSession

    override fun close()

    /** Static factories. */
    companion object {
        /** Creates a fresh solver from [backend], failing explicitly if it is unavailable; [backend] stays open. */
        @JvmStatic
        fun of(backend: SolverBackend): BlockingSolver

        /** Takes ownership of [solver]. */
        @JvmStatic
        fun of(solver: ConstraintSolver): BlockingSolver
    }
}

/** Wraps this solver, which the result owns, in a [BlockingSolver]. */
fun ConstraintSolver.blocking(): BlockingSolver = BlockingSolver.of(this)

/** Creates a [BlockingSolver], runs [block] on it and closes it, also when [block] fails; the backend stays open. */
inline fun <R> SolverBackend.withBlockingSolver(block: (BlockingSolver) -> R): R = BlockingSolver.of(this).use(block)

internal fun ConstraintSolver.openIncrementalSession(model: ConstraintModel): IncrementalSession {
    val incremental =
        this as? IncrementalConstraintSolver
            ?: throw UnsupportedOperationException("${descriptor.id} does not support incremental sessions")
    return incremental.openSession(model)
}
