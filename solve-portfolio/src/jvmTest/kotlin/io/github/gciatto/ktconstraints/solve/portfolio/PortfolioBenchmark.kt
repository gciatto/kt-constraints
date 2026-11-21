package io.github.gciatto.ktconstraints.solve.portfolio

import io.github.gciatto.ktconstraints.solve.SolveOptions
import io.github.gciatto.ktconstraints.solve.SolveResult
import io.github.gciatto.ktconstraints.test.solve.BenchmarkCatalogue
import io.github.gciatto.ktconstraints.test.solve.BenchmarkInstance
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.system.measureNanoTime

/**
 * Manual cross-route benchmark (`./gradlew :solve-portfolio:jvmBenchmark`); never part of routine check. Prints CSV,
 * one row per family, size and eligible route plus an `auto` row. Columns: model build, support check (translation),
 * cold solve on a fresh solver, warm solve on the same solver, and correctness against the known answer. Set
 * `BENCHMARK_SCALE` (default 1) to grow sizes and `BENCHMARK_CSV` to also write a file. Raw observations only.
 */
object PortfolioBenchmark {
    private const val TIMEOUT_MILLIS = 30_000L
    private const val HEADER = "family,size,class,route,status,objective,correct,build_ms,support_ms,cold_ms,warm_ms"

    /** Runs every family on every eligible bundled route. */
    @JvmStatic
    fun main(args: Array<String>): Unit =
        runBlocking {
            val scale = System.getenv("BENCHMARK_SCALE")?.toInt() ?: 1
            val options = SolveOptions(timeoutMillis = TIMEOUT_MILLIS)
            val rows = mutableListOf(HEADER)
            DefaultBackends.load().use { loaded ->
                for (builder in BenchmarkCatalogue.builders(scale)) {
                    lateinit var instance: BenchmarkInstance
                    val build = millis { instance = builder() }
                    val selection = loaded.registry.rank(instance.model, options)
                    for (candidate in selection.candidates) {
                        val backend = candidate.backend
                        val support = millis { backend.support(instance.model, options) }
                        backend.create().use { solver ->
                            lateinit var cold: SolveResult
                            val coldMillis = millis { cold = solver.solve(instance.model, options) }
                            val warmMillis = millis { solver.solve(instance.model, options) }
                            rows +=
                                row(
                                    instance,
                                    selection.features.problemClass.name,
                                    backend.descriptor.id,
                                    cold,
                                    build,
                                    support,
                                    coldMillis,
                                    warmMillis,
                                )
                        }
                    }
                    lateinit var auto: SolveResult
                    val autoMillis = millis { auto = Portfolio(loaded.registry).fallback(instance.model, options) }
                    rows +=
                        row(
                            instance,
                            selection.features.problemClass.name,
                            "auto:${auto.descriptor.id}",
                            auto,
                            build,
                            0.0,
                            autoMillis,
                            autoMillis,
                        )
                    println(rows.last())
                }
            }
            val csv = rows.joinToString("\n")
            println(csv)
            System.getenv("BENCHMARK_CSV")?.let { File(it).writeText(csv + "\n") }
        }

    @Suppress("LongParameterList") // One CSV column each.
    private fun row(
        instance: BenchmarkInstance,
        problemClass: String,
        route: String,
        result: SolveResult,
        build: Double,
        support: Double,
        cold: Double,
        warm: Double,
    ): String {
        val correct = result.status == instance.expectedStatus && result.objectiveValue == instance.expectedObjective
        return listOf(
            instance.family,
            instance.size,
            problemClass,
            route,
            result.status,
            result.objectiveValue ?: "",
            correct,
            "%.2f".format(build),
            "%.2f".format(support),
            "%.2f".format(cold),
            "%.2f".format(warm),
        ).joinToString(",")
    }

    private inline fun millis(block: () -> Unit): Double = measureNanoTime(block) / NANOS_PER_MILLI

    private const val NANOS_PER_MILLI = 1_000_000.0
}
