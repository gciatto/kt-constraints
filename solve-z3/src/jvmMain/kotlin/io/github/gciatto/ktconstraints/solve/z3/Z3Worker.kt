package io.github.gciatto.ktconstraints.solve.z3

import com.microsoft.z3.Context
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean

/** One lazily created Z3 context confined to a dedicated thread; interrupted from any thread, closed on its own. */
internal class Z3Worker : AutoCloseable {
    private val executor =
        Executors.newSingleThreadExecutor { task -> Thread(task, "kt-constraints-z3").apply { isDaemon = true } }
    private val closed = AtomicBoolean(false)

    @Volatile private var context: Context? = null

    /** Whether [close] has been called. */
    val isClosed: Boolean get() = closed.get()

    /** Runs [block] on the context thread; coroutine cancellation interrupts the native call. */
    @Suppress("RedundantSuspendModifier") // detekt misses the suspending suspendCancellableCoroutine call.
    suspend fun <T> await(block: (Context) -> T): T =
        suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { context?.interrupt() }
            try {
                executor.execute { continuation.resumeWith(runCatching { block(owned()) }) }
            } catch (_: RejectedExecutionException) {
                error("Z3 context is closed")
            }
        }

    /** Runs a short non-suspending [block] on the context thread, blocking the caller. */
    fun <T> run(block: (Context) -> T): T =
        try {
            executor.submit(Callable { block(owned()) }).get()
        } catch (_: RejectedExecutionException) {
            error("Z3 context is closed")
        } catch (failure: ExecutionException) {
            throw failure.cause ?: failure
        }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        context?.interrupt()
        executor.execute { context?.close() }
        executor.shutdown()
    }

    private fun owned(): Context {
        check(!closed.get()) { "Z3 context is closed" }
        return context ?: Context().also {
            context = it
            // A concurrent close may have missed the new context; its queued task closes it after this block.
            check(!closed.get()) { "Z3 context is closed" }
        }
    }
}
