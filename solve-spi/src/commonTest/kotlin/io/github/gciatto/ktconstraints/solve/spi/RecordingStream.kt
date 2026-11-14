package io.github.gciatto.ktconstraints.solve.spi

import io.github.gciatto.ktconstraints.solve.SolutionEvent
import io.github.gciatto.ktconstraints.solve.SolverDescriptor

internal class RecordingStream(
    events: List<SolutionEvent> = emptyList(),
    private val failure: Throwable? = null,
    private val readHook: suspend () -> Unit = {},
    private val releaseHook: () -> Unit = {},
    onClosed: () -> Unit = {},
) : ManagedSolutionStream(SolverDescriptor("fake", "stream", emptySet()), onClosed) {
    private val events = events.toMutableList()
    var reads = 0
        private set
    var releases = 0
        private set

    override suspend fun readNext(): SolutionEvent {
        reads++
        readHook()
        failure?.let { throw it }
        return events.removeFirst()
    }

    override fun release() {
        releases++
        releaseHook()
    }
}
