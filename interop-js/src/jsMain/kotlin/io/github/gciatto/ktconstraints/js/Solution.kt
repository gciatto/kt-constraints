@file:OptIn(ExperimentalWasmJsInterop::class)

package io.github.gciatto.ktconstraints.js

import io.github.gciatto.ktconstraints.core.BoolVar
import io.github.gciatto.ktconstraints.core.IntVar
import io.github.gciatto.ktconstraints.solve.Assignment
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

    /** Exact value of an integer variable, or null when it is not projected; other expressions are rejected. */
    fun valueOf(variable: IntExpression): JsBigInt? {
        val declared = variable.expr as? IntVar ?: throw IllegalArgumentException("Not an integer variable: ${variable.expr}")
        return assignment.valueOf(declared)?.toBigInt()
    }

    /** Value of a Boolean variable, or null when it is not projected; other expressions are rejected. */
    fun boolValueOf(variable: BoolExpression): Boolean? {
        val declared = variable.expr as? BoolVar ?: throw IllegalArgumentException("Not a Boolean variable: ${variable.expr}")
        return assignment.valueOf(declared)
    }
}
