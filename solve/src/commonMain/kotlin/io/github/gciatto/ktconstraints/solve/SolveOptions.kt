package io.github.gciatto.ktconstraints.solve

import kotlin.jvm.JvmField
import kotlin.jvm.JvmOverloads

/** Immutable execution preferences. Timeout and limit policies are backend contracts, not model constraints. */
data class SolveOptions
    @JvmOverloads
    constructor(
        val timeoutMillis: Long? = null,
        val solutionLimit: Int? = null,
        /** Per-backend tuning, at most one per type; see [BackendOptions]. */
        val backendOptions: List<BackendOptions> = emptyList(),
    ) {
        init {
            require(timeoutMillis == null || timeoutMillis >= 0L)
            require(solutionLimit == null || solutionLimit > 0)
        }

        /** These options with [options] added, replacing any earlier options of the same type. */
        operator fun plus(options: BackendOptions): SolveOptions =
            copy(backendOptions = backendOptions.filter { it::class != options::class } + options)

        /** Shared default configuration. */
        companion object {
            @JvmField
            val DEFAULT: SolveOptions = SolveOptions()
        }
    }

/** The tuning of type [T] these options carry, or null. */
inline fun <reified T : BackendOptions> SolveOptions.backend(): T? = backendOptions.filterIsInstance<T>().lastOrNull()
