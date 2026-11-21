package io.github.gciatto.ktconstraints.solve.kotlin

import io.github.gciatto.ktconstraints.solve.TerminationReason
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.yield
import kotlin.coroutines.CoroutineContext
import kotlin.time.TimeSource

/** Checks time in batches and yields cooperatively, including inside compiler and theory work. */
internal class SearchBudget(
    private val timeoutMillis: Long?,
    private val workLimit: Long?,
    private val closed: () -> Boolean,
) {
    private val started = TimeSource.Monotonic.markNow()
    private var context: CoroutineContext? = null
    private var nextYield = 0L

    var work: Long = 0
        private set

    fun consume() {
        if (workLimit != null && work >= workLimit) throw SearchStopped(TerminationReason.RESOURCE_LIMIT)
        work++
        if ((work and 255L) == 0L) poll()
    }

    fun poll() {
        context?.ensureActive()
        if (closed()) throw SearchStopped(TerminationReason.CANCELLED)
        if (timeoutMillis != null && started.elapsedNow().inWholeMilliseconds >= timeoutMillis) {
            throw SearchStopped(TerminationReason.TIMEOUT)
        }
    }

    suspend fun checkpoint() {
        context = currentCoroutineContext()
        consume()
        if (work >= nextYield) {
            poll()
            yield()
            nextYield = work + 2048
        }
    }
}
