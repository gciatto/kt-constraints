package io.github.gciatto.ktconstraints.js

import io.github.gciatto.ktconstraints.js.solve.Solver
import io.github.gciatto.ktconstraints.js.store.Store
import io.github.gciatto.ktconstraints.solve.minizinc.MiniZincJsBackend
import io.github.gciatto.ktconstraints.solve.spi.SolverBackend
import io.github.gciatto.ktconstraints.solve.spi.createIncrementalSolver
import io.github.gciatto.ktconstraints.solve.spi.store.SatisfiabilityStore
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

    /** Declared capabilities, such as `ENUMERATION` or `INCREMENTAL_ASSERTIONS`, each proven by conformance tests. */
    val capabilities: Array<String> get() =
        backend.descriptor.capabilities
            .map { it.name }
            .sorted()
            .toTypedArray()

    /** Creates a fresh solver. */
    fun createSolver(): Solver = Solver(backend.createSolver())

    /** Creates a constraint store over a fresh solver; routes without incremental assertions refuse. */
    fun createStore(): Store = Store(SatisfiabilityStore.of(backend.createIncrementalSolver()))

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

/** Loads MiniZinc's official Node API driving an installed CLI with Gecode (`gecode/minizinc-js-cli`). */
@JsExport
fun loadMiniZincNode(executable: String = "minizinc"): Promise<Backend> =
    CoroutineScope(EmptyCoroutineContext).promise {
        val backend = MiniZincJsBackend.loadNode(executable)
        Backend(backend, backend)
    }

/**
 * Loads MiniZinc's browser Wasm with Chuffed (`chuffed/minizinc-wasm`) from the official worker, Wasm and data
 * assets served at the given URLs. Only one MiniZinc backend may be loaded at a time; close it before loading another.
 */
@JsExport
fun loadMiniZincBrowser(
    workerURL: String,
    wasmURL: String,
    dataURL: String,
): Promise<Backend> =
    CoroutineScope(EmptyCoroutineContext).promise {
        val backend = MiniZincJsBackend.loadBrowser(workerURL, wasmURL, dataURL)
        Backend(backend, backend)
    }
