package io.github.gciatto.ktconstraints.model

import io.github.gciatto.ktconstraints.core.Variable
import io.github.gciatto.ktconstraints.core.bool.BoolExpr
import kotlin.jvm.JvmStatic

/** Frozen, solver-independent constraint problem. */
class ConstraintModel internal constructor(
    variables: Collection<Variable>,
    entries: Collection<ConstraintEntry>,
    /** Optional optimization objective; null means satisfaction. */
    val objective: Objective?,
    /** Explicit projection set by [ConstraintScope.project]; null means every variable, including later ones. */
    internal val projection: List<Variable>? = null,
) {
    /** Variables declared in the model, in declaration order. */
    val variables: List<Variable> = buildList { addAll(variables) }

    /** Immutable assertions and metadata, in assertion order. */
    val entries: List<ConstraintEntry> = buildList { addAll(entries) }

    /** Asserted Boolean expressions, preserving the original modelling API. */
    val constraints: List<BoolExpr> = buildList { entries.forEach { add(it.expression) } }

    /** Variables included in public solver output, in projection order. */
    val outputVariables: List<Variable> = projection ?: this.variables

    /** Builds a new model holding this one plus what [block] declares; this model is unchanged. */
    fun extend(block: ConstraintScope.() -> Unit): ConstraintModel = ConstraintScope.of(this).apply(block).buildModel()

    /** Factories for models. */
    companion object {
        /** Builds a model using a Kotlin receiver-based DSL. */
        fun of(block: ConstraintScope.() -> Unit): ConstraintModel = ConstraintScope.empty().apply(block).buildModel()

        /** The model with no variables, constraints or objective. */
        @JvmStatic
        fun empty(): ConstraintModel = ConstraintScope.empty().buildModel()
    }
}
