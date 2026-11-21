package io.github.gciatto.ktconstraints.solve.spi

import io.github.gciatto.ktconstraints.core.AllDifferent
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
import io.github.gciatto.ktconstraints.core.Cumulative
import io.github.gciatto.ktconstraints.core.DecomposableConstraint
import io.github.gciatto.ktconstraints.core.Element
import io.github.gciatto.ktconstraints.core.Expr
import io.github.gciatto.ktconstraints.core.IntDifference
import io.github.gciatto.ktconstraints.core.IntEquality
import io.github.gciatto.ktconstraints.core.IntExpr
import io.github.gciatto.ktconstraints.core.IntLessOrEqual
import io.github.gciatto.ktconstraints.core.IntLessThan
import io.github.gciatto.ktconstraints.core.IntLiteral
import io.github.gciatto.ktconstraints.core.IntMax
import io.github.gciatto.ktconstraints.core.IntMin
import io.github.gciatto.ktconstraints.core.IntNegation
import io.github.gciatto.ktconstraints.core.IntProduct
import io.github.gciatto.ktconstraints.core.IntSum
import io.github.gciatto.ktconstraints.core.IntVar
import io.github.gciatto.ktconstraints.core.LinearSum
import io.github.gciatto.ktconstraints.core.Table
import io.github.gciatto.ktconstraints.core.Variable
import io.github.gciatto.ktconstraints.core.fold
import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.model.Objective
import org.gciatto.kt.math.BigInteger

/**
 * Assessment of the Boolean/integer subset a bounded-integer backend represents exactly: every value and accumulated
 * magnitude must stay within [limit], described as [margin] in rejections (e.g. "the CP-SAT int64 margin of 2^61").
 * When [nestedGlobalsDecompose] holds, globals below the top-level conjunction are reported as lossless decompositions.
 */
open class BoundedIntegerSupport(
    /** Bound on every value and accumulated magnitude. */
    val limit: BigInteger,
    private val margin: String,
    private val nestedGlobalsDecompose: Boolean,
) {
    /** Unsupported constructs and declared decompositions of [model]. */
    fun report(model: ConstraintModel): SupportReport = Assessment().apply { assess(model) }.report()

    /** Unsupported constructs of one top-level [constraint], as posted to an incremental store. */
    fun report(constraint: BoolExpr): SupportReport = Assessment().apply { assess(constraint) }.report()

    /** Whether [variable]'s sort and domain bounds are representable. */
    fun report(variable: Variable): SupportReport = Assessment().apply { assess(variable) }.report()

    /** Exact range of a supported integer expression; empty domains are represented by zero. */
    fun bounds(expression: IntExpr): IntegerBounds = expression.fold { node, children: List<IntegerBounds> -> combine(node, children) }

    private fun combine(
        node: Expr,
        children: List<IntegerBounds>,
    ): IntegerBounds =
        when (node) {
            is IntVar -> {
                val domain = node.domain
                val intervals = domain.intervals
                if (intervals.isEmpty()) {
                    IntegerBounds(
                        BigInteger.ZERO,
                        BigInteger.ZERO,
                    )
                } else {
                    IntegerBounds(intervals.first().min, intervals.last().max)
                }
            }
            is IntLiteral -> IntegerBounds(node.value, node.value)
            is IntSum -> sum(children[0], children[1])
            is IntDifference -> sum(children[0], negate(children[1]))
            is IntNegation -> negate(children.single())
            is LinearSum ->
                children.zip(node.coefficients).fold(IntegerBounds(node.constant, node.constant)) { total, (term, coefficient) ->
                    sum(total, scale(term, coefficient))
                }
            is IntProduct -> product(children[0], children[1])
            is Element -> IntegerBounds(children.drop(1).minOf { it.min }, children.drop(1).maxOf { it.max })
            is IntMin -> IntegerBounds(children.minOf { it.min }, children.minOf { it.max })
            is IntMax -> IntegerBounds(children.maxOf { it.min }, children.maxOf { it.max })
            is BoolToInt, is BoolExpr -> IntegerBounds(BigInteger.ZERO, BigInteger.ONE)
            else -> throw IllegalArgumentException("Unsupported integer expression: $node")
        }

    private fun sum(
        left: IntegerBounds,
        right: IntegerBounds,
    ) = IntegerBounds(left.min + right.min, left.max + right.max, left.magnitude + right.magnitude)

    private fun negate(bounds: IntegerBounds) = IntegerBounds(-bounds.max, -bounds.min, bounds.magnitude)

    private fun scale(
        bounds: IntegerBounds,
        coefficient: BigInteger,
    ): IntegerBounds {
        val scaled = IntegerBounds(bounds.min * coefficient, bounds.max * coefficient, bounds.magnitude * coefficient.absoluteValue)
        return if (coefficient.signum < 0) IntegerBounds(scaled.max, scaled.min, scaled.magnitude) else scaled
    }

    private fun product(
        left: IntegerBounds,
        right: IntegerBounds,
    ): IntegerBounds {
        val corners = listOf(left.min * right.min, left.min * right.max, left.max * right.min, left.max * right.max)
        return IntegerBounds(corners.minOf { it }, corners.maxOf { it })
    }

    /** Collects unsupported constructs and lossless decompositions of one model. */
    private inner class Assessment {
        private val unsupported = linkedSetOf<String>()
        private val decompositions = linkedSetOf<String>()

        fun report() = SupportReport(unsupported, decompositions)

        fun assess(model: ConstraintModel) {
            model.variables.forEach { assess(it) }
            model.constraints.forEach { assess(it) }
            when (val objective = model.objective) {
                is Objective.Minimize -> integer(objective.expression)
                is Objective.Maximize -> integer(objective.expression)
                null -> Unit
            }
        }

        fun assess(variable: Variable) {
            when (variable) {
                is BoolVar -> Unit
                is IntVar -> integer(variable)
                else -> unsupported += "Unsupported variable sort: ${variable.name}"
            }
        }

        fun assess(constraint: BoolExpr) = boolean(constraint, topLevel = true)

        private fun boolean(
            expression: BoolExpr,
            topLevel: Boolean,
        ) {
            when (expression) {
                is BoolVar, is BoolLiteral -> Unit
                is BoolAnd -> expression.children.forEach { boolean(it as BoolExpr, topLevel) }
                is BoolOr, is BoolImplies, is BoolEquality, is BoolNot, is BoolCardinality ->
                    expression.children.forEach { boolean(it as BoolExpr, false) }
                is IntEquality, is IntLessThan, is IntLessOrEqual -> expression.children.forEach { integer(it as IntExpr) }
                is AllDifferent, is Table -> global(expression, topLevel)
                is DecomposableConstraint -> structural(expression, topLevel)
                else -> unsupported += "Unsupported expression: ${expression::class.simpleName ?: expression}"
            }
        }

        private fun global(
            expression: BoolExpr,
            topLevel: Boolean,
        ) {
            expression.children.forEach { integer(it as IntExpr) }
            if (expression is Table) expression.tuples.flatten().forEach { check(it, "Table value") }
            if (nestedGlobalsDecompose && !topLevel) {
                decompositions +=
                    if (expression is Table) {
                        "nested table -> reified disjunction of tuple conjunctions"
                    } else {
                        "nested allDifferent -> reified pairwise disequalities"
                    }
            }
        }

        /** Natively posted operands at top level; the exact decomposition below it, or for cumulative magnitudes. */
        private fun structural(
            expression: DecomposableConstraint,
            topLevel: Boolean,
        ) {
            if (topLevel && expression !is Cumulative) {
                expression.children.forEach { integer(it as IntExpr) }
            } else {
                boolean(expression.decomposition, false)
            }
            if (!topLevel) decompositions += "nested ${expression::class.simpleName} -> its exact decomposition"
        }

        private fun integer(expression: IntExpr) {
            val before = unsupported.size
            expression.fold { node, _: List<Unit> ->
                when (node) {
                    is IntVar, is IntLiteral, is IntSum, is IntDifference, is IntNegation, is IntProduct, is Element, is IntMin,
                    is IntMax, is BoolExpr,
                    -> Unit
                    is BoolToInt -> boolean(node.operand, false)
                    is LinearSum -> {
                        node.coefficients.forEach { check(it, "Coefficient") }
                        check(node.constant, "Constant")
                    }
                    else -> unsupported += "Unsupported expression: ${node::class.simpleName ?: node}"
                }
            }
            if (unsupported.size != before) return
            expression.fold { node, children: List<IntegerBounds> ->
                combine(node, children).also {
                    if (!it.fits(limit)) unsupported += "Integer range of $node exceeds $margin"
                }
            }
        }

        private fun check(
            value: BigInteger,
            what: String,
        ) {
            if (value.absoluteValue > limit) unsupported += "$what $value exceeds $margin"
        }
    }
}
