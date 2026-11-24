@file:OptIn(ExperimentalWasmJsInterop::class)

package io.github.gciatto.ktconstraints.js.solve
import io.github.gciatto.ktconstraints.core.bool.BoolVar
import io.github.gciatto.ktconstraints.core.integer.IntVar
import io.github.gciatto.ktconstraints.core.real.RealVar
import io.github.gciatto.ktconstraints.js.expression.BoolExpression
import io.github.gciatto.ktconstraints.js.expression.IntExpression
import io.github.gciatto.ktconstraints.js.expression.RealExpression
import io.github.gciatto.ktconstraints.js.platform.toBigInt
import io.github.gciatto.ktconstraints.solve.assignment.Assignment
import org.gciatto.kt.math.BigInteger
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsBigInt

/** One validated solution or optimization incumbent. */
@JsExport
class Solution internal constructor(
    private val assignment: Assignment,
    objective: BigInteger?,
) {
    /** Exact objective value of this incumbent, or null for satisfaction. */
    val objective: JsBigInt? = objective?.toBigInt()

    /**
     * Exact value of an integer variable, or null when it is not projected; other expressions are rejected.
     * Not named `valueOf`, which would shadow `Object.prototype.valueOf` and break coercion.
     */
    fun intValueOf(variable: IntExpression): JsBigInt? {
        val declared = variable.expr as? IntVar ?: throw IllegalArgumentException("Not an integer variable: ${variable.expr}")
        return assignment.valueOf(declared)?.toBigInt()
    }

    /** Exact value of a real variable as `p` or `p/q`, or null when it is not projected. */
    fun realValueOf(variable: RealExpression): String? {
        val declared = variable.expr as? RealVar ?: throw IllegalArgumentException("Not a real variable: ${variable.expr}")
        return assignment.valueOf(declared)?.toString()
    }

    /** Value of a Boolean variable, or null when it is not projected; other expressions are rejected. */
    fun boolValueOf(variable: BoolExpression): Boolean? {
        val declared = variable.expr as? BoolVar ?: throw IllegalArgumentException("Not a Boolean variable: ${variable.expr}")
        return assignment.valueOf(declared)
    }
}
