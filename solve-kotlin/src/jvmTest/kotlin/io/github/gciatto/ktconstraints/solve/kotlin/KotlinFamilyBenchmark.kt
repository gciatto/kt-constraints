package io.github.gciatto.ktconstraints.solve.kotlin

import io.github.gciatto.ktconstraints.solve.SolveOptions
import io.github.gciatto.ktconstraints.solve.result.SolveStatus
import io.github.gciatto.ktconstraints.test.solve.benchmark.BenchmarkCatalogue
import io.github.gciatto.ktconstraints.test.solve.benchmark.BenchmarkInstance
import kotlinx.coroutines.runBlocking

/**
 * Manual multi-seed measurements of `kotlin/direct` on the benchmark catalogue (`:solve-kotlin:jvmFamilyBenchmark`).
 * Each family, scale and seed is solved once on a fresh solver after a warm-up; rows report the median, minimum and
 * maximum milliseconds over seeds. A wrong answer aborts the run. Environment: `BENCHMARK_SCALES` (default `4 6`),
 * `BENCHMARK_SEEDS` (default 5) and optionally `BENCHMARK_FAMILIES`.
 */
object KotlinFamilyBenchmark {
    private const val TIMEOUT_MILLIS = 60_000L

    @JvmStatic
    fun main(args: Array<String>): Unit =
        runBlocking {
            val scales = (System.getenv("BENCHMARK_SCALES") ?: "4 6").split(' ', ',').filter { it.isNotBlank() }.map { it.toInt() }
            val seeds = System.getenv("BENCHMARK_SEEDS")?.toInt() ?: 5
            val families =
                System
                    .getenv("BENCHMARK_FAMILIES")
                    ?.split(' ', ',')
                    ?.filter { it.isNotBlank() }
                    ?.toSet()
            warmUp()
            println("family,scale,size,seeds,median_ms,min_ms,max_ms,median_work,timeouts")
            for (scale in scales) {
                for (index in BenchmarkCatalogue.builders(scale).indices) {
                    val samples =
                        (0 until seeds).mapNotNull { seed ->
                            val instance = BenchmarkCatalogue.builders(scale, seed)[index]()
                            if (families != null && instance.family !in families) return@mapNotNull null
                            if (seed > 0 && instance.family !in BenchmarkCatalogue.SEEDED) return@mapNotNull null
                            (instance.family to instance.size) to measure(instance)
                        }
                    if (samples.isEmpty()) continue
                    val (family, size) = samples.first().first
                    // Timeouts sort last, so the median stays meaningful while fewer than half the seeds time out.
                    val millis = samples.map { it.second.first }.sortedBy { if (it < 0) Long.MAX_VALUE else it }
                    val work = samples.map { it.second.second }.sorted()
                    val timeouts = millis.count { it < 0 }
                    println(
                        "$family,$scale,$size,${samples.size},${show(median(millis))},${show(millis.first())},${show(millis.last())}," +
                            "${median(work)},$timeouts",
                    )
                }
            }
        }

    private suspend fun warmUp() {
        repeat(3) { BenchmarkCatalogue.instances(2).forEach { measure(it) } }
    }

    /** Milliseconds and deterministic work units of one cold solve, after checking the known answer. */
    private suspend fun measure(instance: BenchmarkInstance): Pair<Long, Long> {
        val solver = KotlinBackend().create()
        try {
            val started = System.nanoTime()
            val result = solver.solve(instance.model, SolveOptions(timeoutMillis = TIMEOUT_MILLIS))
            val millis = (System.nanoTime() - started) / 1_000_000
            val proven = result.status == SolveStatus.OPTIMAL || result.status == SolveStatus.INFEASIBLE
            val solved = result.status == instance.expectedStatus && result.objectiveValue == instance.expectedObjective
            // A proof must be right; an unproven result within the limit is a timeout, reported as -1 ms.
            val wrong = (proven && !solved) || (result.status == SolveStatus.FEASIBLE && instance.expectedStatus == SolveStatus.INFEASIBLE)
            check(!wrong) {
                "Wrong answer on $instance: ${result.status} ${result.objectiveValue}"
            }
            return (if (solved) millis else -1L) to (result.metadata.statistics["work"]?.toLong() ?: 0L)
        } finally {
            solver.close()
        }
    }

    private fun median(values: List<Long>): Long = values[values.size / 2]

    private fun show(millis: Long): String = if (millis < 0) "timeout" else millis.toString()
}
