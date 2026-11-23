package io.github.gciatto.ktconstraints.solve.stream

import io.github.gciatto.ktconstraints.solve.result.SolveResult

/** Closeable single-consumer asynchronous search; close and cancellation are idempotent. */
interface SolutionStream : AutoCloseable {
    /** Definitive result once completed or cancelled, even when the terminal event has not been consumed. */
    val terminalResult: SolveResult?

    /** Returns the next event, then null after its single completion event. Concurrent reads are rejected. */
    suspend fun next(): SolutionEvent?

    /** Stops execution and releases resources, preserving an already definitive result. */
    fun cancel()

    /** Cancels unfinished execution and releases resources. */
    override fun close()
}
