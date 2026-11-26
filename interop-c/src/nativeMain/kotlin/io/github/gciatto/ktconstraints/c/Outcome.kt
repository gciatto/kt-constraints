package io.github.gciatto.ktconstraints.c

import io.github.gciatto.ktconstraints.core.bool.BoolVar
import io.github.gciatto.ktconstraints.core.integer.IntVar
import io.github.gciatto.ktconstraints.core.real.RealVar
import io.github.gciatto.ktconstraints.solve.result.SolveResult

/** Result of [solve]: a solver's definitive answer, or `ERROR` with a [message] when no solver could run. */
class Outcome internal constructor(
    private val result: SolveResult?,
    /** Why solving failed, when [status] is `ERROR`; otherwise null. */
    val message: String?,
) {
    /** `FEASIBLE`, `OPTIMAL`, `INFEASIBLE`, `UNBOUNDED`, `INFEASIBLE_OR_UNBOUNDED`, `UNKNOWN`, or `ERROR`. */
    val status: String get() = result?.status?.name ?: "ERROR"

    /** Engine and execution route that answered, such as `z3/native`; null on `ERROR`. */
    val route: String? get() = result?.descriptor?.id

    /** Exact objective value in decimal, when optimizing and a solution was found. */
    val objective: String? get() = result?.objectiveValue?.toString()

    /** Exact rational real objective value (`p/q`, or `p` for integers), or null without an incumbent real objective. */
    val realObjective: String? get() = result?.realObjectiveValue?.toString()

    /** Exact value in decimal of the integer [variable], or null without a solution or for non-variables. */
    fun valueOf(variable: IntExpression): String? = (variable.expr as? IntVar)?.let { result?.assignment?.valueOf(it) }?.toString()

    /** Exact rational value of the real [variable], or null without a value or for non-variables. */
    fun valueOfReal(variable: RealExpression): String? = (variable.expr as? RealVar)?.let { result?.assignment?.valueOf(it) }?.toString()

    /** 1 or 0 for the Boolean [variable], or -1 without a solution or for non-variables. */
    fun truthOf(variable: BoolExpression): Int =
        when ((variable.expr as? BoolVar)?.let { result?.assignment?.valueOf(it) }) {
            true -> 1
            false -> 0
            null -> -1
        }
}
