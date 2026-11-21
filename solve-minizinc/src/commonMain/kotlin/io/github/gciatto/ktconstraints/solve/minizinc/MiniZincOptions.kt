package io.github.gciatto.ktconstraints.solve.minizinc

import io.github.gciatto.ktconstraints.solve.BackendOptions

/**
 * Per-solve tuning of the MiniZinc command-line routes: `SolveOptions() + MiniZincOptions(threads = 4)`. Engines
 * without a flag skip it (CBC takes no seed). The JavaScript routes do not apply these options yet.
 */
data class MiniZincOptions(
    /** Fixed random seed (`-r`), or null. */
    val randomSeed: Long? = null,
    /** Solver threads (`-p`), or null. */
    val threads: Int? = null,
    /** Lets the engine ignore search annotations (`-f`). */
    val freeSearch: Boolean = false,
) : BackendOptions {
    init {
        require(threads == null || threads > 0) { "Thread count must be positive: $threads" }
    }

    /** Driver flags for [engine]. */
    fun arguments(engine: MiniZincEngine): List<String> =
        buildList {
            if (engine != MiniZincEngine.CBC) randomSeed?.let { addAll(listOf("-r", it.toString())) }
            threads?.let { addAll(listOf("-p", it.toString())) }
            if (freeSearch) add("-f")
        }
}
