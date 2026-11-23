package io.github.gciatto.ktconstraints.js.incremental
import io.github.gciatto.ktconstraints.js.expression.BoolExpression
import io.github.gciatto.ktconstraints.js.solve.Solution
import io.github.gciatto.ktconstraints.solve.incremental.CheckResult

/** Result of one [Session.check]. */
@JsExport
class CheckOutcome internal constructor(
    result: CheckResult,
    assumptions: Array<BoolExpression>,
) {
    /** `SATISFIABLE`, `UNSATISFIABLE` or `UNKNOWN`. */
    val status: String = result.status.name

    /** Why the check stopped: `COMPLETED`, `TIMEOUT`, `CANCELLED`, ... */
    val termination: String = result.terminationReason.name

    /** Model of the session's variables, present exactly when satisfiable. */
    val solution: Solution? = result.assignment?.let { Solution(it, null) }

    /** The given assumption objects jointly sufficient for unsatisfiability (not necessarily minimal), if any. */
    val unsatCore: Array<BoolExpression>? =
        result.unsatCore?.let { core -> assumptions.filter { it.expr in core }.toTypedArray() }
}
