package io.github.gciatto.ktconstraints.model

import io.github.gciatto.ktconstraints.core.Expr
import io.github.gciatto.ktconstraints.core.Sort
import io.github.gciatto.ktconstraints.core.Variable
import io.github.gciatto.ktconstraints.core.VariableFactory
import io.github.gciatto.ktconstraints.core.VariableId
import io.github.gciatto.ktconstraints.core.bitvector.BitVecExpr
import io.github.gciatto.ktconstraints.core.bitvector.BitVecVar
import io.github.gciatto.ktconstraints.core.bool.BoolExpr
import io.github.gciatto.ktconstraints.core.bool.BoolVar
import io.github.gciatto.ktconstraints.core.domain.IntDomain
import io.github.gciatto.ktconstraints.core.evaluation.fold
import io.github.gciatto.ktconstraints.core.integer.IntExpr
import io.github.gciatto.ktconstraints.core.integer.IntVar
import io.github.gciatto.ktconstraints.core.real.RealExpr
import io.github.gciatto.ktconstraints.core.real.RealVar
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

/** Stateful single-model builder; snapshots created by [build] are detached from later edits. */
class ConstraintScope private constructor() {
    private val factory = VariableFactory.empty()
    private val variables = linkedMapOf<String, Variable>()
    private val ids = hashSetOf<VariableId>()
    private val entries = mutableListOf<ConstraintEntry>()
    private val names = hashSetOf<String>()
    private var objective: Objective? = null
    private var projection: List<Variable>? = null

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

    /** Declares an integer variable over the inclusive interval [min]..[max]; usable from Java. */
    fun intVar(
        name: String,
        min: Long,
        max: Long,
    ): IntVar = intVar(name, IntDomain.of(min, max))

    /** Declares an unbounded exact real variable; routes without exact reals reject models declaring one. */
    fun realVar(name: String): RealVar = addVariable(name) { factory.realVar(name) }

    /** Declares a fixed-width bit-vector variable; routes without bit-vectors reject models declaring one. */
    fun bitVecVar(
        name: String,
        width: Int,
    ): BitVecVar = addVariable(name) { factory.bitVecVar(name, width) }

    /** Asserts a Boolean expression, optionally with a unique name and diagnostic metadata. */
    @JvmOverloads
    fun add(
        constraint: BoolExpr,
        name: String? = null,
        annotations: Map<String, String> = emptyMap(),
        source: SourceLocation? = null,
    ): ConstraintScope =
        apply {
            requireWellSorted(constraint)
            requireOwned(constraint.variables)
            require(name == null || name !in names) { "Duplicate constraint name: $name" }
            entries += ConstraintEntry(constraint, name, annotations, source)
            name?.let { names += it }
        }

    /** Replaces the model objective with a minimization. */
    fun minimize(expression: IntExpr): ConstraintScope =
        apply {
            requireWellSorted(expression)
            requireOwned(expression.variables)
            objective = Objective.Minimize(expression)
        }

    /** Replaces the model objective with a maximization. */
    fun maximize(expression: IntExpr): ConstraintScope =
        apply {
            requireWellSorted(expression)
            requireOwned(expression.variables)
            objective = Objective.Maximize(expression)
        }

    /** Clears any optimization objective, selecting satisfaction. */
    fun satisfy(): ConstraintScope = apply { objective = null }

    /** Replaces the output projection; an empty projection is permitted. */
    fun project(vararg output: Variable): ConstraintScope =
        apply {
            requireOwned(output.toSet())
            require(output.map { it.id }.toSet().size == output.size) { "Duplicate projection variable" }
            projection = buildList { addAll(output) }
        }

    /** Produces a detached read-only snapshot of the current model. */
    fun build(): ConstraintModel = ConstraintModel(variables.values, entries, objective, projection ?: variables.values)

    private fun requireWellSorted(expression: Expr) {
        expression.fold { node, _: List<Unit> ->
            when (node) {
                is IntExpr -> require(node.sort == Sort.Integer) { "Inconsistent integer expression sort" }
                is BoolExpr -> require(node.sort == Sort.Bool) { "Inconsistent Boolean expression sort" }
                is RealExpr -> require(node.sort == Sort.Real) { "Inconsistent real expression sort" }
                is BitVecExpr -> require(node.sort == Sort.BitVector(node.width)) { "Inconsistent bit-vector expression sort" }
            }
        }
    }

    private fun requireOwned(referenced: Set<Variable>) {
        require(referenced.all { it.id in ids }) {
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
        ids += variable.id
        return variable
    }

    /** Static constructor following the 2p-Kt Scope.empty() convention. */
    companion object {
        /** Creates a new empty builder. */
        @JvmStatic
        fun empty(): ConstraintScope = ConstraintScope()
    }
}
