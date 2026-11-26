package io.github.gciatto.ktconstraints.solve.z3

import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.solve.SolveOptions
import io.github.gciatto.ktconstraints.solve.SolverCapability
import io.github.gciatto.ktconstraints.solve.SolverDescriptor
import io.github.gciatto.ktconstraints.solve.incremental.CheckStatus
import io.github.gciatto.ktconstraints.solve.incremental.IncrementalConstraintSolver
import io.github.gciatto.ktconstraints.solve.spi.BackendAvailability
import io.github.gciatto.ktconstraints.solve.spi.SolverBackend
import io.github.gciatto.ktconstraints.solve.spi.SolverBackendLoader
import io.github.gciatto.ktconstraints.solve.spi.exception.BackendUnavailableException
import io.github.gciatto.ktconstraints.solve.spi.support.SupportReport
import io.github.gciatto.ktconstraints.solve.z3.port.JsZ3Owner
import io.github.gciatto.ktconstraints.solve.z3.port.z3SolverModule
import io.github.gciatto.ktconstraints.solve.z3.session.Z3Solver
import io.github.gciatto.ktconstraints.solve.z3.translation.Z3Support
import kotlinx.coroutines.await
import kotlin.js.Promise

/**
 * Z3 through the official z3-solver Wasm module, which this backend owns: closing it closes its solvers and
 * terminates the module's worker threads. Node is supported; browsers additionally need cross-origin isolation.
 */
class Z3JsBackend private constructor(
    private val api: dynamic,
) : SolverBackend {
    private val solvers = mutableListOf<IncrementalConstraintSolver>()
    private var closed = false

    override val descriptor: SolverDescriptor = DESCRIPTOR

    override fun availability(): BackendAvailability =
        if (closed) BackendAvailability(false, "z3-solver module is closed") else BackendAvailability(true)

    override fun support(
        model: ConstraintModel,
        options: SolveOptions,
    ): SupportReport = Z3Support.report(model)

    override fun createSolver(): IncrementalConstraintSolver {
        if (closed) throw BackendUnavailableException("${descriptor.id}: z3-solver module is closed")
        return Z3Solver(descriptor) { JsZ3Owner(api) }.also { solvers += it }
    }

    /** Closes every created solver, then terminates the module's worker threads; idempotent. */
    override fun close() {
        if (closed) return
        closed = true
        try {
            solvers.forEach { it.close() }
        } finally {
            terminateThreads(api.em)
        }
    }

    // Only the Node build exports killThreads; in browsers, terminate the workers the same way it does.
    private fun terminateThreads(em: dynamic) {
        if (jsTypeOf(z3SolverModule.killThreads) == "function") {
            z3SolverModule.killThreads(em)
        } else {
            em.PThread.terminateAllThreads()
            if (jsTypeOf(em.async_cancel) == "function") em.async_cancel(Error("Z3 threads were terminated"))
        }
    }

    /** Asynchronous loading and route identity. */
    companion object : SolverBackendLoader<Z3JsBackend> {
        /** `z3/wasm`: batch solving plus incremental sessions with checkpoints, assumptions and unsat cores. */
        val DESCRIPTOR: SolverDescriptor =
            SolverDescriptor(
                "z3",
                "wasm",
                setOf(
                    SolverCapability.SATISFACTION,
                    SolverCapability.OPTIMIZATION,
                    SolverCapability.ENUMERATION,
                    SolverCapability.INTEGER_ARITHMETIC,
                    SolverCapability.GLOBAL_CONSTRAINTS,
                    SolverCapability.INCREMENTAL_ASSERTIONS,
                    SolverCapability.INCREMENTAL_OPTIMIZATION,
                    SolverCapability.BACKTRACKING,
                    SolverCapability.ASSUMPTIONS,
                    SolverCapability.UNSAT_CORES,
                    SolverCapability.REAL_ARITHMETIC,
                    SolverCapability.BITVECTORS,
                ),
            )

        /** Initializes a fresh z3-solver module and proves it with a real check before returning. */
        override suspend fun load(): Z3JsBackend {
            val api: dynamic =
                runCatching { z3SolverModule.init().unsafeCast<Promise<dynamic>>().await() }
                    .getOrElse { throw BackendUnavailableException("z3-solver cannot start: ${it.message}", it) }
            val backend = Z3JsBackend(api)
            runCatching { probe(api) }.onFailure {
                backend.close()
                throw BackendUnavailableException("z3-solver failed its startup check: ${it.message}", it)
            }
            return backend
        }

        private suspend fun probe(api: dynamic) {
            JsZ3Owner(api).use { owner ->
                val outcome = owner.confined { it.check(emptyList(), null) }
                check(outcome.status == CheckStatus.SATISFIABLE) { "empty check returned ${outcome.status}" }
            }
        }
    }
}
