package io.github.gciatto.ktconstraints.solve.minizinc.runtime
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

internal actual fun runMiniZinc(
    executable: String,
    arguments: List<String>,
    model: String?,
): String {
    val directory = Files.createTempDirectory("kt-constraints-minizinc-probe-")
    var process: Process? = null
    return try {
        val modelArguments = model?.let { listOf(Files.writeString(directory.resolve("probe.mzn"), it).toString()) }.orEmpty()
        val output = directory.resolve("stdout.log")
        val error = directory.resolve("stderr.log")
        val child =
            ProcessBuilder(listOf(executable) + arguments + modelArguments)
                .redirectOutput(output.toFile())
                .redirectError(error.toFile())
                .start()
        process = child
        check(child.waitFor(PROBE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) { "MiniZinc runtime probe timed out" }
        check(child.exitValue() == 0) { "MiniZinc runtime probe failed: ${Files.readString(error)}" }
        Files.readString(output)
    } catch (failure: IOException) {
        throw IllegalStateException(failure.message ?: "Cannot run MiniZinc", failure)
    } catch (interrupted: InterruptedException) {
        Thread.currentThread().interrupt()
        throw IllegalStateException("MiniZinc runtime probe was interrupted", interrupted)
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
        // SIGKILL is asynchronous: close must not return while solver descendants are still alive.
        descendants.forEach { awaitExit(it) }
        process.outputStream.close()
    }
}

private fun awaitExit(handle: ProcessHandle) {
    try {
        handle.onExit().get(2, TimeUnit.SECONDS)
    } catch (interrupted: InterruptedException) {
        Thread.currentThread().interrupt()
    } catch (timeout: TimeoutException) {
        check(!handle.isAlive) { "MiniZinc descendant ${handle.pid()} survived SIGKILL: $timeout" }
    }
}
