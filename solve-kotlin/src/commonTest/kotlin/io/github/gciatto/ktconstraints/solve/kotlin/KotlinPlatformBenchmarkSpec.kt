package io.github.gciatto.ktconstraints.solve.kotlin

import io.github.gciatto.ktconstraints.solve.SolveOptions
import io.github.gciatto.ktconstraints.solve.SolveStatus
import io.github.gciatto.ktconstraints.solve.spi.Platform
import io.github.gciatto.ktconstraints.test.solve.BenchmarkCatalogue
import io.github.gciatto.ktconstraints.test.solve.BenchmarkInstance
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeSource

/**
 * The same seeded catalogue on every target, for cross-platform comparison; skipped unless
 * `KT_CONSTRAINTS_PLATFORM_BENCHMARK` is set. Prints `platform,family,scale,size,seeds,median_ms,min_ms,max_ms` rows
 * after a warm-up; `KT_CONSTRAINTS_PLATFORM_BENCHMARK_SCALES` (default `4 6`) and `_SEEDS` (default 3) size the run.
 */
class KotlinPlatformBenchmarkSpec :
    FunSpec({
        val enabled = benchmarkEnvironment("KT_CONSTRAINTS_PLATFORM_BENCHMARK") != null
        val scales =
            (
                benchmarkEnvironment(
                    "KT_CONSTRAINTS_PLATFORM_BENCHMARK_SCALES",
                ) ?: "4 6"
            ).split(' ', ',').filter { it.isNotBlank() }.map { it.toInt() }
        val seeds = benchmarkEnvironment("KT_CONSTRAINTS_PLATFORM_BENCHMARK_SEEDS")?.toInt() ?: 3

        test("kotlin-platform-benchmark/catalogue").config(enabled = enabled, timeout = 120.minutes) {
            val platform = Platform.current().let { "${it.runtime}-${it.os}".lowercase() }
            repeat(2) { BenchmarkCatalogue.instances(2).forEach { measure(it) } }
            for (scale in scales) {
                for (index in BenchmarkCatalogue.builders(scale).indices) {
                    val first = BenchmarkCatalogue.builders(scale)[index]()
                    val seeded = first.family in BenchmarkCatalogue.SEEDED
                    val distinct =
                        listOf(first) + if (seeded) (1 until seeds).map { BenchmarkCatalogue.builders(scale, it)[index]() } else emptyList()
                    val millis = distinct.map { measure(it) }.sorted()
                    println(
                        "BENCH,$platform,${first.family},$scale,${first.size},${millis.size},${millis[millis.size / 2]},${millis.first()},${millis.last()}",
                    )
                }
            }
        }
    })

/** Milliseconds of one solve on a fresh solver, after checking the known answer; unproven results count as -1. */
private suspend fun measure(instance: BenchmarkInstance): Long {
    val solver = KotlinBackend().create()
    try {
        val started = TimeSource.Monotonic.markNow()
        val result = solver.solve(instance.model, SolveOptions(timeoutMillis = 60_000))
        val millis = started.elapsedNow().inWholeMilliseconds
        if (result.status == SolveStatus.OPTIMAL || result.status == SolveStatus.INFEASIBLE) {
            result.status shouldBe instance.expectedStatus
            result.objectiveValue shouldBe instance.expectedObjective
        }
        return if (result.status == instance.expectedStatus) millis else -1
    } finally {
        solver.close()
    }
}
