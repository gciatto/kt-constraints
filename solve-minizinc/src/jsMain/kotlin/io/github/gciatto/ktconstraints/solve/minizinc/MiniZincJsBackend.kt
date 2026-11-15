package io.github.gciatto.ktconstraints.solve.minizinc

import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.solve.ConstraintSolver
import io.github.gciatto.ktconstraints.solve.SolveOptions
import io.github.gciatto.ktconstraints.solve.SolverDescriptor
import io.github.gciatto.ktconstraints.solve.spi.BackendAvailability
import io.github.gciatto.ktconstraints.solve.spi.BackendUnavailableException
import io.github.gciatto.ktconstraints.solve.spi.SolverBackend
import io.github.gciatto.ktconstraints.solve.spi.SupportReport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.js.Promise

// shortcut: upstream owns one global pool; allow multiple owners when it exposes isolated runtimes.
private var runtimeOwned = false

// Upstream shutdown SIGKILLs CLI runs, preempting MiniZinc's own temporary-file cleanup after SIGINT.
private const val CANCEL_GRACE_MILLIS = 5_000L
private var pendingRelease: Job? = null

/**
 * Owns the official MiniZinc JS runtime and its active streams; close to release workers/processes.
 * Closing cancels streams and releases the runtime after they exit; the next load awaits that release.
 */
class MiniZincJsBackend private constructor(
    /** Engine whose tested finite integer subset is compiled for this runtime. */
    val engine: MiniZincEngine,
    internal val configurationId: String,
    route: String,
    private val settlesPool: Boolean,
) : SolverBackend,
    AutoCloseable {
    override val descriptor: SolverDescriptor =
        SolverDescriptor(engine.engineId, route, engine.descriptor.capabilities)
    private val streams = mutableSetOf<MiniZincJsStream>()
    private var closed = false
    private val pool = Mutex()

    /**
     * Browser workers terminated during importScripts raise uncaught page errors. With one pooled worker,
     * awaiting version() leaves it loaded (it is reused, not replaced), so runs and shutdown only terminate
     * loaded workers. Runs start inside [block] so concurrent starts cannot take a loading replacement.
     */
    internal suspend fun <T> withSettledPool(block: () -> T): T =
        if (settlesPool) {
            pool.withLock {
                awaitVersion()
                block()
            }
        } else {
            block()
        }

    override fun availability(): BackendAvailability =
        if (closed) BackendAvailability(false, "MiniZinc JS runtime is closed") else BackendAvailability(true)

    override fun support(
        model: ConstraintModel,
        options: SolveOptions,
    ): SupportReport = MiniZincCompiler(engine).support(model, options)

    override fun create(): ConstraintSolver {
        requireOpen()
        return MiniZincJsSolver(this)
    }

    internal fun requireOpen() {
        check(!closed) { "MiniZinc JS runtime is closed" }
    }

    internal fun register(stream: MiniZincJsStream) {
        requireOpen()
        streams += stream
    }

    internal fun remove(stream: MiniZincJsStream) {
        streams -= stream
    }

    override fun close() {
        if (closed) return
        closed = true
        val active = streams.toList()
        try {
            active.forEach { it.close() }
        } finally {
            pendingRelease =
                CoroutineScope(Dispatchers.Default).launch {
                    try {
                        withTimeoutOrNull(CANCEL_GRACE_MILLIS) { active.forEach { it.exited.await() } }
                        withSettledPool { }
                    } finally {
                        try {
                            miniZincApi.shutdown()
                        } finally {
                            runtimeOwned = false
                        }
                    }
                }
        }
    }

    /** Async loading verifies the selected engine configuration with an actual solve. */
    companion object {
        /** Loads browser Wasm using explicitly served official worker, Wasm and data assets. */
        suspend fun loadBrowser(
            workerURL: String,
            wasmURL: String,
            dataURL: String,
        ): MiniZincJsBackend {
            check(js("typeof window !== 'undefined' && typeof Worker !== 'undefined'") as Boolean) {
                "Wasm loading requires a browser"
            }
            val config = js("({})")
            config.workerURL = workerURL
            config.wasmURL = wasmURL
            config.dataURL = dataURL
            config.numWorkers = 1
            return load(config, "minizinc-wasm", MiniZincEngine.CHUFFED, "org.minizinc.chuffed", settlesPool = true)
        }

        /** Loads the official Node API, which executes a native CLI and does not use browser Wasm. */
        suspend fun loadNode(executable: String = "minizinc"): MiniZincJsBackend {
            check(js("typeof process !== 'undefined' && process.versions && !!process.versions.node") as Boolean) {
                "Node CLI loading requires Node.js"
            }
            val config = js("({})")
            config.minizinc = executable
            return load(config, "minizinc-js-cli", MiniZincEngine.GECODE, MiniZincEngine.GECODE.configurationId, settlesPool = false)
        }

        private suspend fun load(
            config: dynamic,
            route: String,
            engine: MiniZincEngine,
            configurationId: String,
            settlesPool: Boolean,
        ): MiniZincJsBackend {
            pendingRelease?.join()
            check(!runtimeOwned) { "Close the previous MiniZinc JS backend before loading another global runtime" }
            runtimeOwned = true
            var loaded = false
            try {
                miniZincApi.init(config).unsafeCast<Promise<dynamic>>().await()
                // Upstream solvers() throws uncaught when the CLI cannot run; version() rejects safely first.
                val startFailure =
                    miniZincApi
                        .version()
                        .unsafeCast<Promise<dynamic>>()
                        .then<Throwable?>({ null }, { failure -> failure })
                        .await()
                if (startFailure != null) {
                    throw BackendUnavailableException("MiniZinc JS runtime cannot start: ${startFailure.message}")
                }
                if (miniZincApi
                        .solvers()
                        .unsafeCast<Promise<Array<dynamic>>>()
                        .await()
                        .none { it.id == configurationId }
                ) {
                    throw BackendUnavailableException("$configurationId is not configured in the MiniZinc JS runtime")
                }
                val probe = newMiniZincModel()
                probe.addFile("probe.mzn", "var 0..1: v; solve satisfy; output [show(v)];")
                val options = js("({jsonOutput:false, options:{solver:'org.gecode.gecode', 'all-solutions':true}})")
                options.options.solver = configurationId
                val progress = probe.solve(options)
                val result = Promise<dynamic> { resolve, reject -> progress.then(resolve, reject) }.await()
                check(result.status == "ALL_SOLUTIONS" && result.solution != null) { "MiniZinc JS startup probe failed" }
                loaded = true
                return MiniZincJsBackend(engine, configurationId, route, settlesPool)
            } finally {
                if (!loaded) {
                    try {
                        miniZincApi.shutdown()
                    } finally {
                        runtimeOwned = false
                    }
                }
            }
        }
    }
}

private suspend fun awaitVersion() {
    // Failures surface from the run itself; this only waits for the pooled worker to finish loading.
    miniZincApi
        .version()
        .unsafeCast<Promise<dynamic>>()
        .then<Unit>({}, {})
        .await()
}
