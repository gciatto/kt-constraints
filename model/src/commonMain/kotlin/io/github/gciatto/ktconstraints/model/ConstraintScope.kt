package io.github.gciatto.ktconstraints.model

import io.github.gciatto.ktconstraints.core.BoolExpr
import io.github.gciatto.ktconstraints.core.BoolVar
import io.github.gciatto.ktconstraints.core.IntDomain
import io.github.gciatto.ktconstraints.core.IntExpr
import io.github.gciatto.ktconstraints.core.IntVar
import io.github.gciatto.ktconstraints.core.Variable
import io.github.gciatto.ktconstraints.core.VariableFactory
import kotlin.jvm.JvmStatic

/** Stateful single-model builder; snapshots created by [build] are detached from later edits. */
class ConstraintScope private constructor() {
    private val factory = VariableFactory.empty()
    private val variables = linkedMapOf<String, Variable>()
    private val constraints = mutableListOf<BoolExpr>()
    private var objective: Objective? = null

    /** Declares a Boolean variable. Duplicate names are rejected. */
    fun boolVar(name: String): BoolVar = addVariable(name) { factory.boolVar(name) }

    /** Declares a finite-domain integer variable. */
    fun intVar(
        name: String,
        domain: IntDomain,
    ): IntVar = addVariable(name) { factory.intVar(name, domain) }

    /** Convenience factory for a bounded finite-domain integer variable. */
    fun intVar(
        name: String,
        range: IntRange,
    ): IntVar = intVar(name, IntDomain.of(range))

    /** Asserts a Boolean expression. */
    fun add(constraint: BoolExpr): ConstraintScope =
        apply {
            requireOwned(constraint.variables)
            constraints += constraint
        }

    /** Replaces the model objective with a minimization. */
    fun minimize(expression: IntExpr): ConstraintScope =
        apply {
            requireOwned(expression.variables)
            objective = Objective.Minimize(expression)
        }

    /** Replaces the model objective with a maximization. */
    fun maximize(expression: IntExpr): ConstraintScope =
        apply {
            requireOwned(expression.variables)
            objective = Objective.Maximize(expression)
        }

    /** Produces a detached read-only snapshot of the current model. */
    fun build(): ConstraintModel = ConstraintModel(variables.values, constraints, objective)

    private fun requireOwned(referenced: Set<Variable>) {
        val localIds = variables.values.mapTo(hashSetOf()) { it.id }
        require(referenced.all { it.id in localIds }) {
            "Expression refers to variables outside this modelling scope"
        }
    }

    private fun <V : Variable> addVariable(
        name: String,
        create: () -> V,
    ): V {
        require(name.isNotBlank()) { "Variable name must not be blank" }
        require(name !in variables) { "Duplicate variable name: $name" }
        val variable = create()
        variables[name] = variable
        return variable
    }

    /** Static constructor following the 2p-Kt Scope.empty() convention. */
    companion object {
        /** Creates a new empty builder. */
        @JvmStatic
        fun empty(): ConstraintScope = ConstraintScope()
    }
}
