package io.github.gciatto.ktconstraints.solve.minizinc

import io.github.gciatto.ktconstraints.solve.spi.BackendExecutionException
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.BufferedReader
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class MiniZincProcess(
    private val executable: String,
    private val source: String,
    private val timeoutMillis: Long?,
    private val onTimeout: () -> Unit,
    private val onCancel: () -> Unit,
) : AutoCloseable {
    private val lock = Any()
    private var closed = false
    private var process: Process? = null
    private var reader: BufferedReader? = null
    private var executor: ExecutorService? = null
    private var timer: ScheduledExecutorService? = null
    private var deadline: ScheduledFuture<*>? = null
    private var workspace: Path? = null
    val directory: Path? get() = synchronized(lock) { workspace }
    val processId: Long? get() = synchronized(lock) { process?.pid() }

    suspend fun readLine(): String? {
        start()
        return io { requireNotNull(reader).readLine() }
    }

    suspend fun exitCode(): Int = io { requireNotNull(process).waitFor() }

    fun stderr(): String = synchronized(lock) {
        workspace?.resolve("stderr.log")?.let { Files.readString(it) }.orEmpty()
    }

    internal fun start() = synchronized(lock) {
        check(!closed) { "MiniZinc process is closed" }
        if (process != null) return@synchronized
        val directory = Files.createTempDirectory("kt-constraints-minizinc-")
        workspace = directory
        try {
            val model = directory.resolve("model.mzn")
            Files.writeString(model, source)
            process = ProcessBuilder(executable, "--solver", "org.gecode.gecode", "--json-stream", "--all-solutions",
                "--statistics", model.toString()).redirectError(directory.resolve("stderr.log").toFile()).start()
            reader = requireNotNull(process).inputStream.bufferedReader(Charsets.UTF_8)
            executor = Executors.newSingleThreadExecutor { task -> Thread(task, "kt-constraints-minizinc-output").apply { isDaemon = true } }
            if (timeoutMillis != null) {
                timer = Executors.newSingleThreadScheduledExecutor { task -> Thread(task, "kt-constraints-minizinc-deadline").apply { isDaemon = true } }
                deadline = timer!!.schedule(onTimeout, timeoutMillis, TimeUnit.MILLISECONDS)
            }
        } catch (failure: Exception) {
            close()
            throw BackendExecutionException("Cannot start MiniZinc: ${failure.message}", failure)
        }
    }

    private suspend fun <T> io(action: () -> T): T = suspendCancellableCoroutine { continuation ->
        val worker = synchronized(lock) {
            check(!closed) { "MiniZinc process is closed" }
            requireNotNull(executor).submit {
                try {
                    val value = action()
                    continuation.resume(value)
                } catch (failure: Exception) {
                    continuation.resumeWithException(failure)
                }
            }
        }
        continuation.invokeOnCancellation {
            try { onCancel() } finally { worker.cancel(true) }
        }
    }

    override fun close() = synchronized(lock) {
        if (closed) return@synchronized
        closed = true
        deadline?.cancel(false)
        timer?.shutdown()
        try {
            process?.let { terminateMiniZinc(it) }
        } finally {
            try {
                reader?.close()
            } finally {
                executor?.shutdownNow()
                workspace?.let { check(it.toFile().deleteRecursively()) { "Cannot delete MiniZinc workspace: $it" } }
            }
        }
    }
}
