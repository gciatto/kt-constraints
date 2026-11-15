@file:OptIn(ExperimentalForeignApi::class, ExperimentalAtomicApi::class, ObsoleteWorkersApi::class)

package io.github.gciatto.ktconstraints.solve.z3

import io.github.gciatto.ktconstraints.solve.z3.capi.Z3_ast
import io.github.gciatto.ktconstraints.solve.z3.capi.Z3_context
import io.github.gciatto.ktconstraints.solve.z3.capi.Z3_del_config
import io.github.gciatto.ktconstraints.solve.z3.capi.Z3_del_context
import io.github.gciatto.ktconstraints.solve.z3.capi.Z3_interrupt
import io.github.gciatto.ktconstraints.solve.z3.capi.Z3_mk_config
import io.github.gciatto.ktconstraints.solve.z3.capi.Z3_mk_context
import io.github.gciatto.ktconstraints.solve.z3.capi.Z3_set_error_handler
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.native.concurrent.ObsoleteWorkersApi
import kotlin.native.concurrent.Worker

/** One lazily created Z3 C context confined to a dedicated worker thread; interrupted from any thread, deleted on its own. */
internal class NativeZ3Owner : Z3Owner<Z3_ast> {
    private val worker = Worker.start(name = "kt-constraints-z3")

    // ALIVE, INTERRUPTING (an interrupt call is in flight) or CLOSED; deletion waits for in-flight interrupts.
    private val state = AtomicInt(ALIVE)
    private var context: Z3_context? = null
    private var port: NativeZ3Port? = null

    private val closeRequested = AtomicInt(0)

    override val isClosed: Boolean get() = closeRequested.load() != 0

    override suspend fun <R> confined(block: suspend (Z3Port<Z3_ast>) -> R): R =
        suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { interrupt() }
            try {
                // The worker only ever blocks on this owner's own native calls.
                worker.executeAfter(0L) { continuation.resumeWith(runCatching { runBlocking { block(owned()) } }) }
            } catch (_: IllegalStateException) {
                error("Z3 context is closed")
            }
        }

    override fun <R> confinedNow(block: (Z3Port<Z3_ast>) -> R): R = runBlocking { confined { block(it) } }

    override fun close() {
        if (!closeRequested.compareAndSet(0, 1)) return
        interrupt()
        try {
            worker.executeAfter(0L) { release() }
            worker.requestTermination()
        } catch (_: IllegalStateException) {
            // Already terminated: nothing was created on it.
        }
    }

    private fun interrupt() {
        if (!state.compareAndSet(ALIVE, INTERRUPTING)) return
        try {
            context?.let { Z3_interrupt(it) }
        } finally {
            state.store(ALIVE)
        }
    }

    private fun release() {
        while (!state.compareAndSet(ALIVE, CLOSED)) {
            // An interrupt is in flight from another thread; it finishes promptly.
        }
        port?.release()
        context?.let { Z3_del_context(it) }
        port = null
        context = null
    }

    private fun owned(): NativeZ3Port {
        check(!isClosed) { "Z3 context is closed" }
        port?.let { return it }
        val config = checkNotNull(Z3_mk_config()) { "Z3 could not create a configuration" }
        val created =
            try {
                checkNotNull(Z3_mk_context(config)) { "Z3 could not create a context" }
            } finally {
                Z3_del_config(config)
            }
        Z3_set_error_handler(created, null)
        context = created
        return NativeZ3Port(created).also { port = it }
    }

    private companion object {
        const val ALIVE = 0
        const val INTERRUPTING = 1
        const val CLOSED = 2
    }
}
