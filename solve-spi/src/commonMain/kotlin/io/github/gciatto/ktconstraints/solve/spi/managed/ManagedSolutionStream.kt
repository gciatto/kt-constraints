package io.github.gciatto.ktconstraints.solve.spi.managed
import io.github.gciatto.ktconstraints.solve.SolverDescriptor
import io.github.gciatto.ktconstraints.solve.result.SolveResult
import io.github.gciatto.ktconstraints.solve.result.SolveStatus
import io.github.gciatto.ktconstraints.solve.result.TerminationReason
import io.github.gciatto.ktconstraints.solve.stream.SolutionEvent
import io.github.gciatto.ktconstraints.solve.stream.SolutionFound
import io.github.gciatto.ktconstraints.solve.stream.SolutionStream
import io.github.gciatto.ktconstraints.solve.stream.SolveCompleted
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.coroutines.cancellation.CancellationException

/** Shared single-consumer lifecycle with concurrent cancellation and exactly-once resource release. */
@OptIn(ExperimentalAtomicApi::class)
abstract class ManagedSolutionStream(
    /** Stable engine and route for all emitted results. */
    protected val descriptor: SolverDescriptor,
    private val onClosed: () -> Unit = {},
) : SolutionStream {
    private val state = AtomicReference(StreamState())
    private val reading = AtomicBoolean(false)

    final override val terminalResult: SolveResult? get() = state.load().terminal

    /** Reads a backend event; implementations must tolerate [release] racing with the read. */
    protected abstract suspend fun readNext(): SolutionEvent

    /** Unblocks active reads and prevents late resource acquisition; invoked once by the winning terminal transition. */
    protected abstract fun release()

    final override suspend fun next(): SolutionEvent? {
        check(reading.compareAndSet(false, true)) { "Only one consumer may read a solution stream" }
        try {
            if (terminalResult != null) return deliverTerminal()
            val event =
                try {
                    currentCoroutineContext().ensureActive()
                    readNext().also {
                        currentCoroutineContext().ensureActive()
                        if (it is SolveCompleted) require(it.result.descriptor == descriptor) { "Completion from another backend route" }
                    }
                } catch (failure: Throwable) {
                    if (failure is CancellationException) {
                        fail(failure, TerminationReason.CANCELLED)
                        throw failure
                    }
                    if (terminalResult != null) return deliverTerminal()
                    fail(failure, TerminationReason.SOLVER_FAILURE)
                    throw failure
                }
            return when (event) {
                is SolutionFound -> if (record(event)) event else deliverTerminal()
                is SolveCompleted -> {
                    finish { event.result }
                    deliverTerminal()
                }
            }
        } finally {
            reading.store(false)
        }
    }

    final override fun cancel() = stop(TerminationReason.CANCELLED)

    final override fun close() = cancel()

    /** Stops for an externally observed deadline or resource limit, preserving an incumbent and prior completion. */
    protected fun stop(reason: TerminationReason) {
        require(reason != TerminationReason.COMPLETED) { "Completed search requires an explicit result" }
        finish { interrupted(it, reason) }
    }

    private fun fail(
        failure: Throwable,
        reason: TerminationReason,
    ) {
        try {
            finish { interrupted(it, reason) }
        } catch (cleanup: Throwable) {
            failure.addSuppressed(cleanup)
        }
    }

    private fun record(event: SolutionFound): Boolean {
        while (true) {
            val current = state.load()
            if (current.terminal != null) return false
            if (state.compareAndSet(current, current.copy(incumbent = event))) return true
        }
    }

    private fun finish(result: (StreamState) -> SolveResult) {
        while (true) {
            val current = state.load()
            if (current.terminal != null) return
            if (state.compareAndSet(current, current.copy(terminal = result(current)))) {
                try {
                    release()
                } finally {
                    onClosed()
                }
                return
            }
        }
    }

    private fun deliverTerminal(): SolveCompleted? {
        while (true) {
            val current = state.load()
            val result = current.terminal ?: return null
            if (current.delivered) return null
            if (state.compareAndSet(current, current.copy(delivered = true))) return SolveCompleted(result)
        }
    }

    private fun interrupted(
        current: StreamState,
        reason: TerminationReason,
    ): SolveResult =
        SolveResult(
            if (current.incumbent == null) SolveStatus.UNKNOWN else SolveStatus.FEASIBLE,
            current.incumbent?.assignment,
            descriptor,
            reason,
            objectiveValue = current.incumbent?.objectiveValue,
            realObjectiveValue = current.incumbent?.realObjectiveValue,
        )
}
