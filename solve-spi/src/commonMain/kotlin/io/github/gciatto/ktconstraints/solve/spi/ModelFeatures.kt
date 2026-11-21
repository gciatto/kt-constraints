package io.github.gciatto.ktconstraints.solve.spi

import io.github.gciatto.ktconstraints.core.AllDifferent
import io.github.gciatto.ktconstraints.core.BinPacking
import io.github.gciatto.ktconstraints.core.BitVecVar
import io.github.gciatto.ktconstraints.core.BoolAnd
import io.github.gciatto.ktconstraints.core.BoolCardinality
import io.github.gciatto.ktconstraints.core.BoolEquality
import io.github.gciatto.ktconstraints.core.BoolExpr
import io.github.gciatto.ktconstraints.core.BoolImplies
import io.github.gciatto.ktconstraints.core.BoolLiteral
import io.github.gciatto.ktconstraints.core.BoolNot
import io.github.gciatto.ktconstraints.core.BoolOr
import io.github.gciatto.ktconstraints.core.BoolToInt
import io.github.gciatto.ktconstraints.core.BoolVar
import io.github.gciatto.ktconstraints.core.Circuit
import io.github.gciatto.ktconstraints.core.Cumulative
import io.github.gciatto.ktconstraints.core.Element
import io.github.gciatto.ktconstraints.core.Expr
import io.github.gciatto.ktconstraints.core.IntLiteral
import io.github.gciatto.ktconstraints.core.IntMax
import io.github.gciatto.ktconstraints.core.IntMin
import io.github.gciatto.ktconstraints.core.IntProduct
import io.github.gciatto.ktconstraints.core.IntVar
import io.github.gciatto.ktconstraints.core.Inverse
import io.github.gciatto.ktconstraints.core.LinearSum
import io.github.gciatto.ktconstraints.core.NoOverlap2D
import io.github.gciatto.ktconstraints.core.RealVar
import io.github.gciatto.ktconstraints.core.Regular
import io.github.gciatto.ktconstraints.core.Sort
import io.github.gciatto.ktconstraints.core.SubCircuit
import io.github.gciatto.ktconstraints.core.Table
import io.github.gciatto.ktconstraints.core.fold
import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.model.Objective
import org.gciatto.kt.math.BigInteger

/** What a model uses, computed in one walk; the input of ranking heuristics and of benchmark evidence lookups. */
class ModelFeatures private constructor(
    /** Whether Boolean variables or expressions occur. */
    val usesBooleans: Boolean,
    /** Whether integer variables or expressions occur. */
    val usesIntegers: Boolean,
    /** Whether exact reals occur. */
    val usesReals: Boolean,
    /** Whether bit-vectors occur. */
    val usesBitVectors: Boolean,
    /** Whether a product multiplies two variable factors, in a constraint or the objective. */
    val nonlinear: Boolean,
    /** Whether a relation or global sits under a Boolean connective other than top-level conjunction. */
    val reified: Boolean,
    /** Global constraints used. */
    val globals: Set<GlobalKind>,
    /** Objective shape. */
    val objective: ObjectiveKind,
    /** Declared variables. */
    val variables: Int,
    /** Top-level constraints. */
    val constraints: Int,
    /** Expression nodes across constraints and objective. */
    val nodes: Int,
    /** Largest absolute integer among literals, coefficients and domain bounds. */
    val maxMagnitude: BigInteger,
) {
    /** The family heuristics and benchmark evidence key on; the most specialised sort wins. */
    val problemClass: ProblemClass
        get() =
            when {
                usesBitVectors -> ProblemClass.SMT_BITVECTOR
                usesReals -> ProblemClass.SMT_REAL
                !usesIntegers -> ProblemClass.PURE_BOOLEAN
                globals.isNotEmpty() || reified -> ProblemClass.CP_GLOBALS
                nonlinear || objective == ObjectiveKind.NONLINEAR -> ProblemClass.NONLINEAR_INTEGER
                else -> ProblemClass.LINEAR_INTEGER
            }

    /** Feature walk. */
    companion object {
        /** Extracts the features of [model]. */
        fun of(model: ConstraintModel): ModelFeatures {
            val walk = Walk()
            model.variables.forEach { walk.variable(it.sort, it) }
            model.constraints.forEach { walk.constraint(it) }
            val objective =
                when (val goal = model.objective) {
                    is Objective.Minimize -> walk.objective(goal.expression)
                    is Objective.Maximize -> walk.objective(goal.expression)
                    null -> ObjectiveKind.NONE
                }
            return ModelFeatures(
                usesBooleans = walk.booleans,
                usesIntegers = walk.integers,
                usesReals = walk.reals,
                usesBitVectors = walk.bitVectors,
                nonlinear = walk.nonlinear,
                reified = walk.reified,
                globals = walk.globals.sortedBy { it.ordinal }.toSet(),
                objective = objective,
                variables = model.variables.size,
                constraints = model.constraints.size,
                nodes = walk.nodes,
                maxMagnitude = walk.magnitude,
            )
        }
    }

    /** Accumulates flags over every visited node. */
    private class Walk {
        var booleans = false
        var integers = false
        var reals = false
        var bitVectors = false
        var nonlinear = false
        var reified = false
        val globals = mutableSetOf<GlobalKind>()
        var nodes = 0
        var magnitude: BigInteger = BigInteger.ZERO

        fun variable(
            sort: Sort,
            variable: Any,
        ) {
            when (sort) {
                Sort.Bool -> booleans = true
                Sort.Integer -> integers = true
                Sort.Real -> reals = true
                is Sort.BitVector -> bitVectors = true
            }
            if (variable is IntVar) variable.domain.intervals.forEach { record(it.min).also { _ -> record(it.max) } }
        }

        /** Walks [constraint], descending through top-level conjunctions without counting them as reification. */
        fun constraint(constraint: BoolExpr) {
            val spine = ArrayDeque(listOf(constraint))
            while (spine.isNotEmpty()) {
                val node = spine.removeLast()
                if (node is BoolAnd) {
                    nodes++
                    spine += node.right
                    spine += node.left
                } else {
                    visit(node)
                }
            }
        }

        fun objective(expression: Expr): ObjectiveKind {
            val before = nonlinear
            nonlinear = false
            visit(expression)
            val kind = if (nonlinear) ObjectiveKind.NONLINEAR else ObjectiveKind.LINEAR
            nonlinear = before || nonlinear
            return kind
        }

        /** One fold per expression; each node reports whether it contains a variable and whether it is a relation. */
        private fun visit(expression: Expr) {
            expression.fold { node, children: List<NodeFacts> ->
                nodes++
                note(node, children)
                NodeFacts(
                    variable = node is BoolVar || node is IntVar || node is RealVar || node is BitVecVar || children.any { it.variable },
                    relation = isRelation(node) || children.any { it.relation },
                )
            }
        }

        private fun note(
            node: Expr,
            children: List<NodeFacts>,
        ) {
            when (node.sort) {
                Sort.Bool -> booleans = true
                Sort.Integer -> integers = true
                Sort.Real -> reals = true
                is Sort.BitVector -> bitVectors = true
            }
            when (node) {
                is IntProduct -> if (children.all { it.variable }) nonlinear = true
                is IntLiteral -> record(node.value)
                is LinearSum -> (node.coefficients + node.constant).forEach { record(it) }
                is AllDifferent -> globals += GlobalKind.ALL_DIFFERENT
                is Table -> globals += GlobalKind.TABLE
                is Element -> globals += GlobalKind.ELEMENT
                is BoolCardinality -> globals += GlobalKind.CARDINALITY
                is IntMin, is IntMax -> globals += GlobalKind.MIN_MAX
                is Inverse -> globals += GlobalKind.INVERSE
                is Circuit -> globals += GlobalKind.CIRCUIT
                is Cumulative -> globals += GlobalKind.CUMULATIVE
                is Regular -> globals += GlobalKind.REGULAR
                is SubCircuit -> globals += GlobalKind.SUBCIRCUIT
                is BinPacking -> globals += GlobalKind.BIN_PACKING
                is NoOverlap2D -> globals += GlobalKind.NO_OVERLAP_2D
                else -> Unit
            }
            if (node.isConnective() && children.any { it.relation }) reified = true
        }

        private fun record(value: BigInteger) {
            magnitude = magnitude.max(value.absoluteValue)
        }
    }
}

/** Whether a node is a Boolean connective, whose relation operands would need reification. */
private fun Expr.isConnective(): Boolean =
    this is BoolOr ||
        this is BoolNot ||
        this is BoolToInt ||
        this is BoolImplies ||
        this is BoolEquality ||
        this is BoolCardinality ||
        this is BoolAnd

/** Whether a Boolean node is a relation or global rather than a variable, literal or connective. */
private fun isRelation(node: Expr): Boolean = node is BoolExpr && node !is BoolVar && node !is BoolLiteral && !node.isConnective()
