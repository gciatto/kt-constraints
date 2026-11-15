package io.github.gciatto.ktconstraints.solve.minizinc

import io.github.gciatto.ktconstraints.solve.spi.BackendAvailability
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.TimeUnit

private const val PROBE_TIMEOUT_SECONDS = 10L

internal fun probeMiniZinc(
    executable: String,
    engine: MiniZincEngine = MiniZincEngine.GECODE,
): BackendAvailability {
    val directory = Files.createTempDirectory("kt-constraints-minizinc-probe-")
    var process: Process? = null
    return try {
        fun run(arguments: List<String>): String {
            val output = directory.resolve("stdout.log")
            val error = directory.resolve("stderr.log")
            val child = ProcessBuilder(listOf(executable) + arguments).redirectOutput(output.toFile()).redirectError(error.toFile()).start()
            process = child
            check(child.waitFor(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) { "MiniZinc runtime probe timed out" }
            check(child.exitValue() == 0) { "MiniZinc runtime probe failed: ${Files.readString(error)}" }
            return Files.readString(output)
        }
        val solvers = Json.parseToJsonElement(run(listOf("--solvers-json"))).jsonArray
        check(solvers.any { it.jsonObject["id"]?.jsonPrimitive?.content == engine.configurationId }) {
            "${engine.configurationId} is not configured in MiniZinc"
        }
        val model = directory.resolve("probe.mzn")
        Files.writeString(model, "var 0..1: v; solve satisfy; output [show(v)];")
        val output = run(listOf("--solver", engine.configurationId, "--json-stream") + engine.searchArguments() + model.toString())
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
    } catch (failure: IOException) {
        unavailable(failure)
    } catch (failure: IllegalArgumentException) {
        unavailable(failure)
    } catch (failure: IllegalStateException) {
        unavailable(failure)
    } catch (interrupted: InterruptedException) {
        Thread.currentThread().interrupt()
        unavailable(interrupted)
    } finally {
        process?.let { terminateMiniZinc(it) }
        check(directory.toFile().deleteRecursively()) { "Cannot delete MiniZinc probe workspace" }
    }
}

internal fun terminateMiniZinc(process: Process) {
    val descendants = process.descendants().toList()
    process.destroy()
    descendants.forEach { it.destroy() }
    try {
        if (!process.waitFor(2, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            process.waitFor(2, TimeUnit.SECONDS)
        }
    } catch (interrupted: InterruptedException) {
        Thread.currentThread().interrupt()
        process.destroyForcibly()
    } finally {
        descendants.filter { it.isAlive }.forEach { it.destroyForcibly() }
        process.outputStream.close()
    }
}

private fun unavailable(failure: Throwable): BackendAvailability =
    BackendAvailability(false, failure.message ?: "MiniZinc runtime probe failed")

internal fun MiniZincEngine.searchArguments(): List<String> =
    when (this) {
        MiniZincEngine.HIGHS -> listOf("--intermediate-solutions", "--highs-mip_rel_gap", "0", "--highs-mip_abs_gap", "0")
        MiniZincEngine.CBC -> listOf("--intermediate-solutions", "--cbc-allowableGap", "0", "--cbc-ratioGap", "0")
        else -> listOf("--all-solutions")
    }
