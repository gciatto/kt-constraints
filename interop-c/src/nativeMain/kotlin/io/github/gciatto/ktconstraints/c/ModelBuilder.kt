package io.github.gciatto.ktconstraints.c

import io.github.gciatto.ktconstraints.model.ConstraintScope

/**
 * Mutable single-model builder; [build] returns a detached immutable [Model]. Invalid input yields a null handle or
 * `false` instead of an exception, since Kotlin exceptions abort the calling C process.
 */
class ModelBuilder {
    private val scope = ConstraintScope.empty()

    /** Declares an integer variable over [min]..[max]; null if the name is taken or the interval is empty. */
    fun intVar(
        name: String,
        min: Long,
        max: Long,
    ): IntExpression? = runCatching { IntExpression(scope.intVar(name, min, max)) }.getOrNull()

    /** Declares a Boolean variable; null if the name is taken. */
    fun boolVar(name: String): BoolExpression? = runCatching { BoolExpression(scope.boolVar(name)) }.getOrNull()

    /** Asserts [constraint]; false if it mentions variables of another builder. */
    fun add(constraint: BoolExpression): Boolean = runCatching { scope.add(constraint.expr) }.isSuccess

    /** Replaces the objective with minimizing [objective]; false if it mentions variables of another builder. */
    fun minimize(objective: IntExpression): Boolean = runCatching { scope.minimize(objective.expr) }.isSuccess

    /** Replaces the objective with maximizing [objective]; false if it mentions variables of another builder. */
    fun maximize(objective: IntExpression): Boolean = runCatching { scope.maximize(objective.expr) }.isSuccess

    /** Snapshots the current model. */
    fun build(): Model = Model(scope.buildModel())
}
