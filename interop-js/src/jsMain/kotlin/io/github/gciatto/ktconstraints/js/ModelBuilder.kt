@file:OptIn(ExperimentalWasmJsInterop::class)

package io.github.gciatto.ktconstraints.js

import io.github.gciatto.ktconstraints.core.BoolNot
import io.github.gciatto.ktconstraints.core.BoolToInt
import io.github.gciatto.ktconstraints.core.IntDomain
import io.github.gciatto.ktconstraints.core.LinearSum
import io.github.gciatto.ktconstraints.model.ConstraintScope
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsBigInt

/** Mutable single-model builder; [build] returns a detached immutable [Model]. */
@JsExport
class ModelBuilder {
    private val scope = ConstraintScope.empty()

    /** Declares an integer variable over the inclusive interval [min]..[max]; names must be unique. */
    fun intVar(
        name: String,
        min: JsBigInt,
        max: JsBigInt,
    ): IntExpression = IntExpression(scope.intVar(name, IntDomain.of(min.toBigInteger(), max.toBigInteger())))

    /** Declares a Boolean variable; names must be unique. */
    fun boolVar(name: String): BoolExpression = BoolExpression(scope.boolVar(name))

    /** Declares an unbounded exact real variable; routes without exact reals reject models declaring one. */
    fun realVar(name: String): RealExpression = RealExpression(scope.realVar(name))

    /** Asserts [constraint]. */
    fun add(constraint: BoolExpression): ModelBuilder = apply { scope.add(constraint.expr) }

    /** Replaces the objective with minimizing [objective]. */
    fun minimize(objective: IntExpression): ModelBuilder = apply { scope.minimize(objective.expr) }

    /** Replaces the objective with maximizing [objective]. */
    fun maximize(objective: IntExpression): ModelBuilder = apply { scope.maximize(objective.expr) }

    /**
     * Soft constraints: replaces the objective with minimizing the total non-negative weight of the violated
     * [constraints], `weights[i]` for `constraints[i]`, and returns that penalty.
     */
    fun minimizeViolations(
        constraints: Array<BoolExpression>,
        weights: Array<JsBigInt>,
    ): IntExpression {
        require(constraints.size == weights.size) { "One weight per soft constraint" }
        val coefficients = weights.map { it.toBigInteger() }
        require(coefficients.all { it.signum >= 0 }) { "Soft-constraint weights must be non-negative" }
        val penalty = LinearSum(constraints.map { BoolToInt(BoolNot(it.expr)) }, coefficients)
        scope.minimize(penalty)
        return IntExpression(penalty)
    }

    /** Clears any objective. */
    fun satisfy(): ModelBuilder = apply { scope.satisfy() }

    /** Snapshots the current model. */
    fun build(): Model = Model(scope.build())
}
