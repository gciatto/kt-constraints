package io.github.gciatto.ktconstraints.solve.stream

import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.solve.ConstraintSolver
import io.github.gciatto.ktconstraints.solve.SolveOptions
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

/**
 * Reads the stream to its end, passing each solution or incumbent to [onSolution], and returns the terminal result.
 * The stream stays open; close it, typically with `use`.
 */
suspend inline fun SolutionStream.forEachSolution(onSolution: (SolutionFound) -> Unit): SolveResult {
    while (true) {
        when (val event = next()) {
            is SolutionFound -> onSolution(event)
            is SolveCompleted -> return event.result
            null -> return checkNotNull(terminalResult) { "Stream ended without a result" }
        }
    }
}

/**
 * Enumerates [model]'s solutions, passing each to [onSolution], and returns the terminal result.
 * The stream is closed afterwards, also when [onSolution] fails.
 */
suspend inline fun ConstraintSolver.forEachSolution(
    model: ConstraintModel,
    options: SolveOptions = SolveOptions.DEFAULT,
    onSolution: (SolutionFound) -> Unit,
): SolveResult = solutions(model, options).use { it.forEachSolution(onSolution) }
