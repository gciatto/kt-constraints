package io.github.gciatto.ktconstraints.solve.spi

/** Runtime probe result, independent of model compatibility and advertised capabilities. */
data class BackendAvailability(
    /** Whether the engine can be started on this platform. */
    val available: Boolean,
    /** Explanation for an unavailable runtime. */
    val reason: String? = null,
) {
    init {
        require(available || !reason.isNullOrBlank()) { "Unavailable runtimes must explain why" }
    }
}
