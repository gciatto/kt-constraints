package io.github.gciatto.ktconstraints.js.platform

/** The standard JavaScript cancellation signal, from the DOM or Node globals. */
external interface AbortSignal {
    /** Whether abortion was already requested. */
    val aborted: Boolean

    /** Registers [listener] for the `abort` event. */
    fun addEventListener(
        type: String,
        listener: () -> Unit,
    )
}
