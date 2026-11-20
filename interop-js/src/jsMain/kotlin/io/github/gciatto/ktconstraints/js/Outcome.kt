@file:OptIn(ExperimentalWasmJsInterop::class)

package io.github.gciatto.ktconstraints.js

import io.github.gciatto.ktconstraints.solve.SolveResult
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsBigInt

/** Definitive result of a search; proof statuses rest on the backend's evidence. */
@JsExport
class Outcome internal constructor(
    result: SolveResult,
) {
    /** `FEASIBLE`, `OPTIMAL`, `INFEASIBLE`, `UNBOUNDED`, `INFEASIBLE_OR_UNBOUNDED` or `UNKNOWN`. */
    val status: String = result.status.name

    /** Why the search stopped: `COMPLETED`, `TIMEOUT`, `CANCELLED`, `INTERRUPTED`, `RESOURCE_LIMIT` or `SOLVER_FAILURE`. */
    val termination: String = result.terminationReason.name

    /** Engine and execution route, such as `z3/wasm`. */
    val route: String = result.descriptor.id

    /** Best solution found, if any. */
    val solution: Solution? = result.assignment?.let { Solution(it, result.objectiveValue) }

    /** Exact objective value of [solution], when optimizing. */
    val objective: JsBigInt? = result.objectiveValue?.toBigInt()

    /** Exact proven objective bound, when the backend supplies one. */
    val bestBound: JsBigInt? = result.bestBound?.toBigInt()
}
