package io.github.gciatto.ktconstraints.solve.auto

import io.github.gciatto.ktconstraints.core.Rational
import io.github.gciatto.ktconstraints.solve.NumericTolerance
import io.github.gciatto.ktconstraints.solve.SolveOptions
import io.github.gciatto.ktconstraints.solve.SolverCapability
import io.github.gciatto.ktconstraints.solve.result.SolveStatus
import io.github.gciatto.ktconstraints.solve.spi.Platform
import io.github.gciatto.ktconstraints.solve.spi.SolverBackend
import io.github.gciatto.ktconstraints.solve.spi.ranking.RankingPolicy
import io.github.gciatto.ktconstraints.solve.spi.withSolver
import io.github.gciatto.ktconstraints.test.solve.benchmark.BenchmarkCatalogue
import io.github.gciatto.ktconstraints.test.solve.benchmark.BenchmarkInstance
import io.github.gciatto.ktconstraints.test.solve.benchmark.LinearCatalogue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CancellationException
import kotlin.time.Duration.Companion.hours
import kotlin.time.TimeSource

/**
 * Every bundled route of this platform on the seeded catalogue, as evidence for the order of backend
 * selection; skipped unless `KT_CONSTRAINTS_ROUTE_BENCHMARK` is set. After a warm-up, prints one
 * `ROUTE,platform,family,class,scale,size,variables,constraints,route,rank,solved,seeds,median_ms,min_ms,max_ms` row
 * per instance and eligible route, where `rank` is the route's position in the order `AutoBackend.load()` tries
 * them and unsolved seeds count as the timeout. `_SCALES` (default `1 2 3`), `_SEEDS` (default 3),
 * `_TIMEOUT` (milliseconds, default 10000), `_FAMILIES` (`catalogue`, `linear`, or both) and `_ROUTES` (route ids, default all)
 * size the run. A wrong
 * proof fails the run; floating-point routes' real optima are compared within a relative 1e-4.
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
        // `catalogue`, `linear` or both (default): the CP/SAT families, the LP/MILP ones.
        val families = benchmarkEnvironment("KT_CONSTRAINTS_ROUTE_BENCHMARK_FAMILIES") ?: "catalogue linear"
        // Comma- or space-separated route ids to measure, or all eligible routes.
        val only = benchmarkEnvironment("KT_CONSTRAINTS_ROUTE_BENCHMARK_ROUTES")?.split(' ', ',')?.filter { it.isNotBlank() }?.toSet()
        val catalogues =
            listOfNotNull(
                Catalogue(BenchmarkCatalogue::builders, BenchmarkCatalogue.SEEDED).takeIf { "catalogue" in families },
                Catalogue(LinearCatalogue::builders, LinearCatalogue.SEEDED).takeIf { "linear" in families },
            )

        test("route-benchmark/catalogue").config(enabled = enabled, timeout = 6.hours, invocationTimeout = 6.hours) {
            val platform = Platform.current().let { "${it.runtime}-${it.os}".lowercase() }
            // A tolerance lets floating-point routes take part in the linear families; their optima are compared within it.
            val options = SolveOptions(timeoutMillis = timeout, numericTolerance = NumericTolerance())
            AutoBackend.load().use { loaded ->
                fun routes(instance: BenchmarkInstance): List<SolverBackend> =
                    loaded.registry
                        .rank(instance.model, options, policy = RankingPolicy.REGISTRATION_ORDER)
                        .candidates
                        .map { it.backend }
                        .filter { only == null || it.descriptor.id in only }
                catalogues.forEach { catalogue ->
                    catalogue.builders(1, 0).forEach { build ->
                        build().let { instance -> routes(instance).forEach { measure(it, instance, options) } }
                    }
                }
                for (catalogue in catalogues) {
                    for (scale in scales) {
                        for (index in catalogue.builders(scale, 0).indices) {
                            val first = catalogue.builders(scale, 0)[index]()
                            val seeded = first.family in catalogue.seeded
                            val distinct =
                                listOf(first) +
                                    if (seeded) (1 until seeds).map { catalogue.builders(scale, it)[index]() } else emptyList()
                            val selection = loaded.registry.rank(first.model, options, policy = RankingPolicy.REGISTRATION_ORDER)
                            val ranked = selection.candidates.map { it.backend.descriptor.id }
                            val features = selection.features
                            for (route in routes(first)) {
                                val millis = distinct.map { measure(route, it, options) }
                                val sorted = millis.map { if (it < 0) timeout else it }.sorted()
                                val rank = ranked.indexOf(route.descriptor.id)
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
        }
    })

/** A family catalogue: builders by scale and seed, and its seeded families. */
private class Catalogue(
    val builders: (Int, Int) -> List<() -> BenchmarkInstance>,
    val seeded: Set<String>,
)

/** Milliseconds of one solve on a fresh solver, after checking any proof; -1 when the route did not answer. */
private suspend fun measure(
    backend: SolverBackend,
    instance: BenchmarkInstance,
    options: SolveOptions,
): Long {
    val started = TimeSource.Monotonic.markNow()
    val result =
        try {
            backend.withSolver { it.solve(instance.model, options) }
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
        instance.expectedRealObjective?.let { expected ->
            val found = requireNotNull(result.realObjectiveValue)
            if (SolverCapability.FLOATING_POINT_REALS in backend.descriptor.capabilities) {
                val gap = (found - expected).let { if (it < Rational.ZERO) -it else it }
                (gap <= Rational.of(1, 10_000) * maxOf(Rational.ONE, if (expected < Rational.ZERO) -expected else expected)) shouldBe true
            } else {
                found shouldBe expected
            }
        }
    }
    return if (result.status == instance.expectedStatus) millis else -1
}
