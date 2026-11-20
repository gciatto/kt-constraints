package io.github.gciatto.ktconstraints.js

import io.github.gciatto.ktconstraints.solve.spi.SolverBackend
import io.github.gciatto.ktconstraints.solve.z3.Z3JsBackend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.promise
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.js.Promise

/** A loaded backend runtime; closing it closes its solvers and releases the runtime. */
@JsExport
class Backend internal constructor(
    private val backend: SolverBackend,
    private val runtime: AutoCloseable,
) {
    /** Engine and execution route, such as `z3/wasm`. */
    val id: String get() = backend.descriptor.id

    /** Creates a fresh solver. */
    fun create(): Solver = Solver(backend.create())

    /** Closes every created solver and releases the runtime; idempotent. */
    fun close() = runtime.close()
}

/** Loads the official z3-solver Wasm module (Node, or browsers with cross-origin isolation) and checks it works. */
@JsExport
fun loadZ3(): Promise<Backend> =
    CoroutineScope(EmptyCoroutineContext).promise {
        val backend = Z3JsBackend.load()
        Backend(backend, backend)
    }
