package io.github.gciatto.ktconstraints.solve.spi.managed
import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.solve.ConstraintSolver
import io.github.gciatto.ktconstraints.solve.SolveOptions
import io.github.gciatto.ktconstraints.solve.result.SolveResult
import io.github.gciatto.ktconstraints.solve.result.SolveStatus
import io.github.gciatto.ktconstraints.solve.stream.SolutionFound
import io.github.gciatto.ktconstraints.solve.stream.SolutionStream
import io.github.gciatto.ktconstraints.solve.stream.SolveCompleted
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Owns active streams and implements batch solving by consuming a resource-safe stream: satisfaction stops at the
 * first solution, optimization reads until the stream completes.
 */
@OptIn(ExperimentalAtomicApi::class)
abstract class ManagedConstraintSolver : ConstraintSolver {
    private val state = AtomicReference(SolverState())

    /** Creates a cold stream; [onClosed] removes it from this solver's active resources. */
    protected abstract fun openStream(
        model: ConstraintModel,
        options: SolveOptions,
        onClosed: () -> Unit,
    ): ManagedSolutionStream

    final override fun solutions(
        model: ConstraintModel,
        options: SolveOptions,
    ): SolutionStream {
        check(!state.load().closed) { "Solver is closed" }
        lateinit var stream: ManagedSolutionStream
        stream = openStream(model, options) { remove(stream) }
        while (true) {
            val current = state.load()
            if (current.closed) {
                stream.close()
                error("Solver is closed")
            }
            if (state.compareAndSet(current, current.copy(streams = current.streams + stream))) return stream
        }
    }

    final override suspend fun solve(
        model: ConstraintModel,
        options: SolveOptions,
    ): SolveResult {
        val stream = solutions(model, options)
        try {
            while (true) {
                when (val event = checkNotNull(stream.next()) { "Stream ended without completion" }) {
                    is SolveCompleted -> return event.result
                    // A satisfaction witness completes the batch request; closing the stream stops the search.
                    is SolutionFound ->
                        if (model.objective == null) return SolveResult(SolveStatus.FEASIBLE, event.assignment, descriptor)
                }
            }
        } finally {
            stream.close()
        }
    }

    /** Whether [close] has started; solvers must reject new resources once true. */
    protected val isClosed: Boolean get() = state.load().closed

    /** Releases solver-owned resources other than streams, such as sessions; invoked once by [close]. */
    protected open fun onClose() = Unit

    final override fun close() {
        while (true) {
            val current = state.load()
            if (current.closed) return
            if (state.compareAndSet(current, SolverState(closed = true))) {
                var failure: Throwable? = null
                try {
                    onClose()
                } catch (problem: Throwable) {
                    failure = problem
                }
                for (stream in current.streams) {
                    try {
                        stream.close()
                    } catch (problem: Throwable) {
                        if (failure == null) failure = problem else failure.addSuppressed(problem)
                    }
                }
                failure?.let { throw it }
                return
            }
        }
    }

    private fun remove(stream: ManagedSolutionStream) {
        while (true) {
            val current = state.load()
            if (stream !in current.streams) return
            if (state.compareAndSet(current, current.copy(streams = current.streams - stream))) return
        }
    }
}
