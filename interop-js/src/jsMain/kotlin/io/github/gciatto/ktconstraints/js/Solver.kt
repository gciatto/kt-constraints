package io.github.gciatto.ktconstraints.js

import io.github.gciatto.ktconstraints.solve.ConstraintSolver
import io.github.gciatto.ktconstraints.solve.SolutionFound
import io.github.gciatto.ktconstraints.solve.SolveCompleted
import io.github.gciatto.ktconstraints.solve.SolveOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.promise
import kotlin.js.Promise

/** One solver instance; closing it cancels pending promises and open searches. */
@JsExport
class Solver internal constructor(
    private val solver: ConstraintSolver,
) {
    private val scope = CoroutineScope(SupervisorJob())

    /** Engine and execution route, such as `z3/wasm`. */
    val id: String get() = solver.descriptor.id

    /**
     * Solves [model]: satisfaction resolves with the first solution, optimization with the final result. Aborting
     * [signal] cancels the search and rejects the promise.
     */
    fun solve(
        model: Model,
        timeoutMillis: Int? = null,
        solutionLimit: Int? = null,
        signal: AbortSignal? = null,
    ): Promise<Outcome> {
        val search = scope.async { Outcome(solver.solve(model.model, options(timeoutMillis, solutionLimit))) }
        signal?.let { if (it.aborted) search.cancel() else it.addEventListener("abort") { search.cancel() } }
        return scope.promise { search.await() }
    }

    /** Iterates solutions or improving incumbents, then one completion event; leaving `for await` early closes it. */
    fun solutions(
        model: Model,
        timeoutMillis: Int? = null,
        solutionLimit: Int? = null,
    ): AsyncIterable<SolutionEvent> {
        val stream = solver.solutions(model.model, options(timeoutMillis, solutionLimit))
        return asyncIterable(
            next = {
                scope.promise {
                    when (val event = stream.next()) {
                        is SolutionFound -> SolutionEvent(Solution(event.assignment, event.objectiveValue), null)
                        is SolveCompleted -> SolutionEvent(null, Outcome(event.result))
                        null -> null
                    }
                }
            },
            close = stream::close,
        )
    }

    /** Cancels pending work and releases the solver; idempotent. */
    fun close() {
        scope.cancel()
        solver.close()
    }

    private fun options(
        timeoutMillis: Int?,
        solutionLimit: Int?,
    ) = SolveOptions(timeoutMillis?.toLong(), solutionLimit)
}
