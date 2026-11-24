package io.github.gciatto.ktconstraints.solve.blocking

import io.github.gciatto.ktconstraints.solve.result.SolveResult
import io.github.gciatto.ktconstraints.solve.stream.SolutionEvent
import io.github.gciatto.ktconstraints.solve.stream.SolutionStream
import kotlinx.coroutines.runBlocking

/**
 * Blocking single-consumer iterator over a [SolutionStream]: solutions or incumbents, then exactly one completion,
 * so Kotlin can write `for (event in stream)` and Java a `while (stream.hasNext())` loop. [hasNext] may block while
 * the backend searches; on the JVM, interrupting the reading thread cancels the search. Close it when stopping early.
 */
class BlockingSolutionStream internal constructor(
    private val stream: SolutionStream,
) : Iterator<SolutionEvent>,
    AutoCloseable {
    private var lookahead: SolutionEvent? = null
    private var exhausted = false

    /** Definitive result once completed or cancelled, even before the completion event is read. */
    val terminalResult: SolveResult? get() = stream.terminalResult

    override fun hasNext(): Boolean {
        if (lookahead == null && !exhausted) {
            lookahead = read()
            exhausted = lookahead == null
        }
        return lookahead != null
    }

    override fun next(): SolutionEvent {
        hasNext()
        val event = lookahead ?: throw NoSuchElementException("The search has completed")
        lookahead = null
        return event
    }

    private fun read(): SolutionEvent? = runBlocking { stream.next() }

    /** Stops the search, preserving an already definitive result. */
    fun cancel() = stream.cancel()

    override fun close() = stream.close()
}
