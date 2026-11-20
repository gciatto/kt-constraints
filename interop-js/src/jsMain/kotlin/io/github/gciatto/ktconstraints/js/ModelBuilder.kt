@file:OptIn(ExperimentalWasmJsInterop::class)

package io.github.gciatto.ktconstraints.js

import io.github.gciatto.ktconstraints.core.IntDomain
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

    /** Asserts [constraint]. */
    fun add(constraint: BoolExpression): ModelBuilder = apply { scope.add(constraint.expr) }

    /** Replaces the objective with minimizing [objective]. */
    fun minimize(objective: IntExpression): ModelBuilder = apply { scope.minimize(objective.expr) }

    /** Replaces the objective with maximizing [objective]. */
    fun maximize(objective: IntExpression): ModelBuilder = apply { scope.maximize(objective.expr) }

    /** Clears any objective. */
    fun satisfy(): ModelBuilder = apply { scope.satisfy() }

    /** Snapshots the current model. */
    fun build(): Model = Model(scope.build())
}
