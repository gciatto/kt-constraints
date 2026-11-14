package io.github.gciatto.ktconstraints.solve.minizinc

import io.github.gciatto.ktconstraints.solve.spi.BackendAvailability
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.util.concurrent.TimeUnit

internal fun probeMiniZinc(executable: String): BackendAvailability {
    val directory = Files.createTempDirectory("kt-constraints-minizinc-probe-")
    var process: Process? = null
    try {
        fun run(vararg arguments: String): String {
            val output = directory.resolve("stdout.log")
            val error = directory.resolve("stderr.log")
            val child = ProcessBuilder(executable, *arguments).redirectOutput(output.toFile()).redirectError(error.toFile()).start()
            process = child
            check(child.waitFor(10, TimeUnit.SECONDS)) { "MiniZinc runtime probe timed out" }
            check(child.exitValue() == 0) { "MiniZinc runtime probe failed: ${Files.readString(error)}" }
            return Files.readString(output)
        }
        val solvers = Json.parseToJsonElement(run("--solvers-json")).jsonArray
        if (solvers.none { it.jsonObject["id"]?.jsonPrimitive?.content == "org.gecode.gecode" }) {
            return BackendAvailability(false, "Gecode is not configured in MiniZinc")
        }
        val model = directory.resolve("probe.mzn")
        Files.writeString(model, "var 0..1: v; solve satisfy; output [show(v)];")
        val output = run("--solver", "org.gecode.gecode", "--json-stream", "--all-solutions", model.toString())
        check(
            output.lineSequence().filter { it.isNotBlank() }.any {
                Json
                    .parseToJsonElement(it)
                    .jsonObject["type"]
                    ?.jsonPrimitive
                    ?.content == "solution"
            },
        ) { "Gecode startup probe produced no solution" }
        return BackendAvailability(true)
    } catch (failure: Exception) {
        return BackendAvailability(false, failure.message ?: "MiniZinc runtime probe failed")
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
