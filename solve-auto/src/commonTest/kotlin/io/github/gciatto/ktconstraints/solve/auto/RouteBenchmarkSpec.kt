package io.github.gciatto.ktconstraints.solve.auto

import io.github.gciatto.ktconstraints.solve.SolveOptions
import io.github.gciatto.ktconstraints.solve.kotlin.KotlinBackend
import io.github.gciatto.ktconstraints.solve.result.SolveStatus
import io.github.gciatto.ktconstraints.solve.spi.Platform
import io.github.gciatto.ktconstraints.solve.spi.SolverBackend
import io.github.gciatto.ktconstraints.test.solve.benchmark.BenchmarkCatalogue
import io.github.gciatto.ktconstraints.test.solve.benchmark.BenchmarkInstance
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CancellationException
import kotlin.time.Duration.Companion.hours
import kotlin.time.TimeSource

/**
 * Every bundled route of this platform, plus `kotlin/direct`, on the seeded catalogue, as evidence for backend
 * selection; skipped unless `KT_CONSTRAINTS_ROUTE_BENCHMARK` is set. After a warm-up, prints one
 * `ROUTE,platform,family,class,scale,size,variables,constraints,route,rank,solved,seeds,median_ms,min_ms,max_ms` row
 * per instance and eligible route, where `rank` is the route's position under the default policy (`-` when it is
 * not registered) and unsolved seeds count as the timeout. `_SCALES` (default `1 2 3`), `_SEEDS` (default 3) and
 * `_TIMEOUT` (milliseconds, default 10000) size the run. A wrong proof fails the run.
 */
class RouteBenchmarkSpec :
    FunSpec({
        val enabled = benchmarkEnvironment("KT_CONSTRAINTS_ROUTE_BENCHMARK") != null
        val scales =
            (benchmarkEnvironment("KT_CONSTRAINTS_ROUTE_BENCHMARK_SCALES") ?: "1 2 3")
                .split(' ', ',')
                .filter { it.isNotBlank() }
                .map { it.toInt() }
        val seeds = benchmarkEnvironment("KT_CONSTRAINTS_ROUTE_BENCHMARK_SEEDS")?.toInt() ?: 3
        val timeout = benchmarkEnvironment("KT_CONSTRAINTS_ROUTE_BENCHMARK_TIMEOUT")?.toLong() ?: 10_000L

        test("route-benchmark/catalogue").config(enabled = enabled, timeout = 6.hours, invocationTimeout = 6.hours) {
            val platform = Platform.current().let { "${it.runtime}-${it.os}".lowercase() }
            val options = SolveOptions(timeoutMillis = timeout)
            DefaultBackends.load().use { loaded ->
                fun routes(instance: BenchmarkInstance): List<SolverBackend> =
                    loaded.registry
                        .rank(instance.model, options)
                        .candidates
                        .map { it.backend } +
                        listOf(KotlinBackend()).filter { it.support(instance.model, options).isSupported }
                BenchmarkCatalogue.instances(1).forEach { instance -> routes(instance).forEach { measure(it, instance, options) } }
                for (scale in scales) {
                    for (index in BenchmarkCatalogue.builders(scale).indices) {
                        val first = BenchmarkCatalogue.builders(scale)[index]()
                        val seeded = first.family in BenchmarkCatalogue.SEEDED
                        val distinct =
                            listOf(first) +
                                if (seeded) (1 until seeds).map { BenchmarkCatalogue.builders(scale, it)[index]() } else emptyList()
                        val selection = loaded.registry.rank(first.model, options)
                        val ranked = selection.candidates.map { it.backend.descriptor.id }
                        val features = selection.features
                        for (route in routes(first)) {
                            val millis = distinct.map { measure(route, it, options) }
                            val sorted = millis.map { if (it < 0) timeout else it }.sorted()
                            val rank = ranked.indexOf(route.descriptor.id).let { if (it < 0) "-" else "$it" }
                            println(
                                listOf(
                                    "ROUTE",
                                    platform,
                                    first.family,
                                    features.problemClass,
                                    scale,
                                    first.size,
                                    features.variables,
                                    features.constraints,
                                    route.descriptor.id,
                                    rank,
                                    millis.count { it >= 0 },
                                    millis.size,
                                    sorted[sorted.size / 2],
                                    sorted.first(),
                                    sorted.last(),
                                ).joinToString(","),
                            )
                        }
                    }
                }
            }
        }
    })

/** Milliseconds of one solve on a fresh solver, after checking any proof; -1 when the route did not answer. */
private suspend fun measure(
    backend: SolverBackend,
    instance: BenchmarkInstance,
    options: SolveOptions,
): Long {
    val started = TimeSource.Monotonic.markNow()
    val result =
        try {
            backend.create().use { it.solve(instance.model, options) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (
            @Suppress("TooGenericExceptionCaught") failure: Exception, // A crashing route is an unsolved instance here.
        ) {
            println("ROUTE-FAILURE,${backend.descriptor.id},${instance.family},${failure.message}")
            return -1
        }
    val millis = started.elapsedNow().inWholeMilliseconds
    if (result.status == SolveStatus.OPTIMAL || result.status == SolveStatus.INFEASIBLE) {
        result.status shouldBe instance.expectedStatus
        result.objectiveValue shouldBe instance.expectedObjective
    }
    return if (result.status == instance.expectedStatus) millis else -1
}
