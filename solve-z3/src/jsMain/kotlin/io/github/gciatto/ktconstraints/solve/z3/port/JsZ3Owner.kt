package io.github.gciatto.ktconstraints.solve.z3.port
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Lazily creates one z3-solver context; a mutex serializes coroutines around its asynchronous checks. */
internal class JsZ3Owner(
    private val api: dynamic,
) : Z3Owner<Any> {
    private val mutex = Mutex()
    private var port: JsZ3Port? = null
    private var closed = false

    override val isClosed: Boolean get() = closed

    override suspend fun <R> confined(block: suspend (Z3Port<Any>) -> R): R = mutex.withLock { block(owned()) }

    override fun <R> confinedNow(block: (Z3Port<Any>) -> R): R {
        check(mutex.tryLock()) { "Z3 context is busy" }
        try {
            return block(owned())
        } finally {
            mutex.unlock()
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        port?.interrupt()
    }

    private fun owned(): JsZ3Port {
        check(!closed) { "Z3 context is closed" }
        return port ?: JsZ3Port(js("Reflect.construct")(api.Context, arrayOf("kt-constraints-${contexts++}"))).also { port = it }
    }

    private companion object {
        var contexts = 0
    }
}
