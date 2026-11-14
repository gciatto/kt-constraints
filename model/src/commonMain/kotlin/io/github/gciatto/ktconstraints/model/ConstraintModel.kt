package io.github.gciatto.ktconstraints.model

import io.github.gciatto.ktconstraints.core.BoolExpr
import io.github.gciatto.ktconstraints.core.Variable

/** Frozen, solver-independent constraint problem. */
class ConstraintModel internal constructor(
    variables: Collection<Variable>,
    entries: Collection<ConstraintEntry>,
    /** Optional optimization objective; null means satisfaction. */
    val objective: Objective?,
    outputVariables: Collection<Variable> = variables,
) {
    /** Variables declared in the model, in declaration order. */
    val variables: List<Variable> = buildList { addAll(variables) }

    /** Immutable assertions and metadata, in assertion order. */
    val entries: List<ConstraintEntry> = buildList { addAll(entries) }

    /** Asserted Boolean expressions, preserving the original modelling API. */
    val constraints: List<BoolExpr> = buildList { entries.forEach { add(it.expression) } }

    /** Variables included in public solver output, in projection order. */
    val outputVariables: List<Variable> = buildList { addAll(outputVariables) }

    /** Creates a fresh modelling scope for a new model. */
    companion object {
        /** Builds a model using a Kotlin receiver-based DSL. */
        fun of(block: ConstraintScope.() -> Unit): ConstraintModel = ConstraintScope.empty().apply(block).build()
    }
}
