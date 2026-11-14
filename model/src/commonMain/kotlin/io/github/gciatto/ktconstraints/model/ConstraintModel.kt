package io.github.gciatto.ktconstraints.model

import io.github.gciatto.ktconstraints.core.BoolExpr
import io.github.gciatto.ktconstraints.core.Variable

/** Frozen, solver-independent constraint problem. */
class ConstraintModel internal constructor(
    /** Variables declared in the model, in declaration order. */
    val variables: List<Variable>,
    /** Asserted Boolean expressions. */
    val constraints: List<BoolExpr>,
    /** Optional optimization objective; null means satisfaction. */
    val objective: Objective?,
) {
    /** Creates a fresh modelling scope for a new model. */
    companion object {
        /** Builds a model using a Kotlin receiver-based DSL. */
        fun of(block: ConstraintScope.() -> Unit): ConstraintModel = ConstraintScope.empty().apply(block).build()
    }
}
