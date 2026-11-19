package io.github.gciatto.ktconstraints.solve

import kotlin.jvm.JvmField

/** Immutable execution preferences. Timeout and limit policies are backend contracts, not model constraints. */
data class SolveOptions(
    val timeoutMillis: Long? = null,
    val solutionLimit: Int? = null,
) {
    init {
        require(timeoutMillis == null || timeoutMillis >= 0L)
        require(solutionLimit == null || solutionLimit > 0)
    }

    /** Shared default configuration. */
    companion object {
        @JvmField
        val DEFAULT: SolveOptions = SolveOptions()
    }
}
