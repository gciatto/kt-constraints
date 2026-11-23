package io.github.gciatto.ktconstraints.c

import io.github.gciatto.ktconstraints.model.ConstraintModel

/** Immutable model snapshot, built by [ModelBuilder.build]. */
class Model internal constructor(
    internal val model: ConstraintModel,
) {
    /** Number of declared variables. */
    val variableCount: Int get() = model.variables.size

    /** Number of asserted constraints. */
    val constraintCount: Int get() = model.constraints.size
}
