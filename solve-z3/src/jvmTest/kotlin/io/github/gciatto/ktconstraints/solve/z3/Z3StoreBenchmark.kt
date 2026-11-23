package io.github.gciatto.ktconstraints.solve.z3

import io.github.gciatto.ktconstraints.solve.spi.store.SatisfiabilityStore
import io.github.gciatto.ktconstraints.test.solve.benchmark.benchmarkStore
import kotlinx.coroutines.runBlocking

/** Manual store benchmark (`./gradlew :solve-z3:jvmStoreBenchmark`); never part of routine check. */
object Z3StoreBenchmark {
    /** Prints per-operation means for N-queens labeling; N comes from `STORE_BENCHMARK_QUEENS` (default 8). */
    @JvmStatic
    fun main(args: Array<String>) =
        runBlocking {
            val queens = System.getenv("STORE_BENCHMARK_QUEENS")?.toInt() ?: DEFAULT_QUEENS
            println("z3/direct satisfiability store, $queens-queens")
            println(benchmarkStore(queens, { SatisfiabilityStore.of(Z3Backend().create()) }, { Z3Backend().create() }))
        }

    private const val DEFAULT_QUEENS = 8
}
