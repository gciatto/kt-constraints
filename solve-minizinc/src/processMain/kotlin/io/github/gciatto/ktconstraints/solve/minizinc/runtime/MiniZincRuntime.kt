package io.github.gciatto.ktconstraints.solve.minizinc.runtime

import io.github.gciatto.ktconstraints.solve.minizinc.MiniZincEngine
import io.github.gciatto.ktconstraints.solve.spi.BackendAvailability
import io.github.gciatto.ktconstraints.solve.spi.exception.BackendExecutionException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal const val PROBE_TIMEOUT_MILLIS = 10_000L

private const val PROBE_MODEL = "var 0..1: v; solve satisfy; output [show(v)];"

/**
 * Runs [executable] with [arguments], plus [model] written to a temporary file as the last argument when given, and
 * returns its standard output. Fails with [IllegalStateException] when it cannot start, times out or exits non-zero.
 */
internal expect fun runMiniZinc(
    executable: String,
    arguments: List<String>,
    model: String? = null,
): String

/** Available only when [engine]'s exact configuration is installed and solves a startup model. */
internal fun probeMiniZinc(
    executable: String,
    engine: MiniZincEngine = MiniZincEngine.GECODE,
): BackendAvailability =
    try {
        val solvers = Json.parseToJsonElement(runMiniZinc(executable, listOf("--solvers-json"))).jsonArray
        check(solvers.any { it.jsonObject["id"]?.jsonPrimitive?.content == engine.configurationId }) {
            "${engine.configurationId} is not configured in MiniZinc"
        }
        val output = runMiniZinc(executable, engine.solverArguments(), PROBE_MODEL)
        check(
            output.lineSequence().filter { it.isNotBlank() }.any {
                Json
                    .parseToJsonElement(it)
                    .jsonObject["type"]
                    ?.jsonPrimitive
                    ?.content == "solution"
            },
        ) { "${engine.engineId} startup probe produced no solution" }
        BackendAvailability(true)
    } catch (failure: IllegalArgumentException) {
        unavailable(failure)
    } catch (failure: IllegalStateException) {
        unavailable(failure)
    } catch (failure: BackendExecutionException) {
        unavailable(failure)
    }

private fun unavailable(failure: Throwable): BackendAvailability =
    BackendAvailability(false, failure.message ?: "MiniZinc runtime probe failed")

internal fun MiniZincEngine.searchArguments(): List<String> =
    when (this) {
        MiniZincEngine.HIGHS -> listOf("--intermediate-solutions", "--highs-mip_rel_gap", "0", "--highs-mip_abs_gap", "0")
        MiniZincEngine.CBC -> listOf("--intermediate-solutions", "--cbc-allowableGap", "0", "--cbc-ratioGap", "0")
        else -> listOf("--all-solutions")
    }

/** Flags selecting [this] engine with JSON output, its complete search, and [tuning]. */
internal fun MiniZincEngine.solverArguments(tuning: List<String> = emptyList()): List<String> =
    listOf("--solver", configurationId, "--json-stream") + searchArguments() + tuning
