package io.github.gciatto.ktconstraints.solve.z3.port
import com.microsoft.z3.Context
import com.microsoft.z3.Expr
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean

/** One lazily created Z3 context confined to a dedicated thread; interrupted from any thread, closed on its own. */
internal class JavaZ3Owner : Z3Owner<Expr<*>> {
    private val executor =
        Executors.newSingleThreadExecutor { task -> Thread(task, "kt-constraints-z3").apply { isDaemon = true } }
    private val closed = AtomicBoolean(false)

    @Volatile private var context: Context? = null
    private var port: JavaZ3Port? = null

    override val isClosed: Boolean get() = closed.get()

    @Suppress("RedundantSuspendModifier") // detekt misses the suspending suspendCancellableCoroutine call.
    override suspend fun <R> confined(block: suspend (Z3Port<Expr<*>>) -> R): R =
        suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { context?.interrupt() }
            try {
                // The dedicated thread only ever blocks on this owner's own native calls.
                executor.execute { continuation.resumeWith(runCatching { runBlocking { block(owned()) } }) }
            } catch (_: RejectedExecutionException) {
                error("Z3 context is closed")
            }
        }

    override fun <R> confinedNow(block: (Z3Port<Expr<*>>) -> R): R =
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

    private fun owned(): JavaZ3Port {
        check(!closed.get()) { "Z3 context is closed" }
        port?.let { return it }
        val created = Context()
        context = created
        // A concurrent close may have missed the new context; its queued task closes it after this block.
        check(!closed.get()) { "Z3 context is closed" }
        return JavaZ3Port(created).also { port = it }
    }
}
