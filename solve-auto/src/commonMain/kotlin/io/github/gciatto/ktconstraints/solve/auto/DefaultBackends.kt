package io.github.gciatto.ktconstraints.solve.auto

import io.github.gciatto.ktconstraints.solve.kotlin.KotlinBackend
import io.github.gciatto.ktconstraints.solve.spi.SolverBackend
import io.github.gciatto.ktconstraints.solve.spi.ranking.Heuristics
import io.github.gciatto.ktconstraints.solve.spi.ranking.RankingPolicy

/**
 * Routes bundled for the current platform, in tie-breaking order, and how to rank them:
 * - JVM: CP-SAT, Choco, Z3, SCIP, HiGHS (60 s per-solve limit, since OR-Tools cannot interrupt it), and the
 *   MiniZinc engines when `MINIZINC_EXECUTABLE` is set;
 * - Node: Z3 Wasm, and MiniZinc's CLI route when `MINIZINC_EXECUTABLE` is set;
 * - browsers: Z3 Wasm, which needs cross-origin isolation;
 * - Native: Z3 through its C API, loaded at run time.
 * Every platform also registers the pure-Kotlin route as its last resort: it answers only models that no other
 * available route takes. Routes whose runtime is missing stay registered and are rejected, with reasons, during
 * ranking.
 */
internal expect object DefaultBackends {
    /** Loads the bundled routes; closeable ones own asynchronously loaded runtimes. */
    suspend fun load(): List<SolverBackend>

    /** The platform's ranking of the bundled routes, refining the caller's [base] policy. */
    fun policy(base: RankingPolicy): RankingPolicy
}

private const val LAST_RESORT_WEIGHT = 1_000.0

/** [base], ranking the pure-Kotlin route after every other eligible route. */
internal fun kotlinAsLastResort(base: RankingPolicy): RankingPolicy =
    base.with(Heuristics.lastResort(setOf(KotlinBackend().descriptor.engine)), LAST_RESORT_WEIGHT)
