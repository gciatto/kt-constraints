package io.github.gciatto.ktconstraints.solve.minizinc.runtime

import io.github.gciatto.ktconstraints.solve.spi.exception.BackendExecutionException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

private const val MODEL = "model.mzn"
private const val ERRORS = "stderr.log"

@OptIn(ExperimentalAtomicApi::class)
internal actual class MiniZincProcess actual constructor(
    private val executable: String,
    private val source: String,
    private val timeoutMillis: Long?,
    private val onTimeout: () -> Unit,
    private val onCancel: () -> Unit,
    /** Driver flags selecting the engine, its search and any tuning. */
    private val solverArguments: List<String>,
) : AutoCloseable {
    // Critical sections are short and never block, so a spin lock is enough.
    private val lock = AtomicBoolean(false)
    private var closed = false
    private var reading = false
    private var outputOpen = false
    private var reaped = false
    private var child: NativeChild? = null
    private var workspace: String? = null
    private var deadline: Job? = null

    actual val directory: String? get() = locked { workspace }
    actual val processId: Long? get() = locked { child?.pid?.toLong() }

    actual suspend fun readLine(): String? {
        start()
        val current =
            locked {
                check(!closed) { "MiniZinc process is closed" }
                reading = true
                requireNotNull(child)
            }
        try {
            return io { current.readLine() }
        } finally {
            // A close that raced with this read left the pipe open, since closing it under a blocked read is unsafe.
            val closeOutput =
                locked {
                    reading = false
                    (closed && outputOpen).also { if (it) outputOpen = false }
                }
            if (closeOutput) current.closeOutput()
        }
    }

    actual suspend fun exitCode(): Int {
        val current = locked { requireNotNull(child) }
        val code = io { current.waitFor() }
        locked { reaped = true }
        return code
    }

    actual fun stderr(): String = locked { workspace }?.let { readText("$it/$ERRORS") }.orEmpty()

    private fun start() =
        locked {
            check(!closed) { "MiniZinc process is closed" }
            if (child != null) return@locked
            try {
                val directory = createWorkspace("kt-constraints-minizinc-")
                workspace = directory
                writeText("$directory/$MODEL", source)
                child =
                    NativeChild.start(
                        listOf(executable) + solverArguments + listOf("--statistics", "$directory/$MODEL"),
                        null,
                        "$directory/$ERRORS",
                    )
                outputOpen = true
            } catch (failure: IllegalStateException) {
                throw BackendExecutionException("Cannot start MiniZinc: ${failure.message}", failure)
            }
            timeoutMillis?.let { millis ->
                deadline =
                    CoroutineScope(Dispatchers.Default).launch {
                        delay(millis)
                        onTimeout()
                    }
            }
        }

    /** Runs a blocking [action] off the caller's thread; cancelling the caller kills the process to unblock it. */
    private suspend fun <T> io(action: () -> T): T =
        coroutineScope {
            val task = async(Dispatchers.IO) { action() }
            try {
                task.await()
            } catch (cancelled: CancellationException) {
                onCancel()
                throw cancelled
            }
        }

    actual override fun close() {
        var current: NativeChild? = null
        var closeOutput = false
        var wasReaped = false
        var directory: String? = null
        locked {
            if (closed) return
            closed = true
            deadline?.cancel()
            current = child
            closeOutput = outputOpen && !reading
            if (closeOutput) outputOpen = false
            wasReaped = reaped
            directory = workspace
        }
        try {
            // Closing an unread pipe first makes the driver's final writes fail instead of blocking its shutdown.
            if (closeOutput) current?.closeOutput()
        } finally {
            try {
                current?.terminate(wasReaped)
            } finally {
                directory?.let { deleteWorkspace(it, MODEL, ERRORS) }
            }
        }
    }

    private inline fun <T> locked(block: () -> T): T {
        while (!lock.compareAndSet(false, true)) {
            // Spin: the holder never blocks.
        }
        try {
            return block()
        } finally {
            lock.store(false)
        }
    }
}
