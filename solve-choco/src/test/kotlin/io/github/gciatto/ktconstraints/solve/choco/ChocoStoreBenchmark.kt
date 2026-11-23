package io.github.gciatto.ktconstraints.solve.choco

import io.github.gciatto.ktconstraints.test.solve.benchmark.benchmarkStore
import kotlinx.coroutines.runBlocking

/** Manual store benchmark (`./gradlew :solve-choco:storeBenchmark`); never part of routine check. */
object ChocoStoreBenchmark {
    /** Prints per-operation means for N-queens labeling; N comes from `STORE_BENCHMARK_QUEENS` (default 8). */
    @JvmStatic
    fun main(args: Array<String>) =
        runBlocking {
            val queens = System.getenv("STORE_BENCHMARK_QUEENS")?.toInt() ?: DEFAULT_QUEENS
            println("choco/direct store, $queens-queens")
            println(benchmarkStore(queens, { ChocoStore() }, { ChocoBackend().create() }))
        }

    private const val DEFAULT_QUEENS = 8
}
