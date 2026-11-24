package io.github.gciatto.ktconstraints.solve.auto

import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/** A budget of [timeoutMillis] shared by every attempt of one portfolio solve; null means unlimited. */
internal class Deadline(
    private val timeoutMillis: Long?,
) {
    private val started = TimeSource.Monotonic.markNow()

    /** Whole milliseconds left, rounded down so that a sub-millisecond remainder counts as spent; null when unlimited. */
    fun remaining(): Long? = timeoutMillis?.let { (it.milliseconds - started.elapsedNow()).inWholeMilliseconds.coerceAtLeast(0) }

    /** Whether a limited budget is spent. */
    val expired: Boolean get() = remaining() == 0L
}
