package io.github.gciatto.ktconstraints.solve.linear

import io.github.gciatto.ktconstraints.core.Expr
import io.github.gciatto.ktconstraints.core.Rational
import io.github.gciatto.ktconstraints.core.Variable
import io.github.gciatto.ktconstraints.core.VariableId
import io.github.gciatto.ktconstraints.core.bool.BoolAnd
import io.github.gciatto.ktconstraints.core.bool.BoolExpr
import io.github.gciatto.ktconstraints.core.bool.BoolLiteral
import io.github.gciatto.ktconstraints.core.bool.BoolNot
import io.github.gciatto.ktconstraints.core.bool.BoolToInt
import io.github.gciatto.ktconstraints.core.bool.BoolVar
import io.github.gciatto.ktconstraints.core.integer.IntExpr
import io.github.gciatto.ktconstraints.core.integer.IntLiteral
import io.github.gciatto.ktconstraints.core.integer.IntToReal
import io.github.gciatto.ktconstraints.core.integer.IntVar
import io.github.gciatto.ktconstraints.core.integer.arithmetic.IntDifference
import io.github.gciatto.ktconstraints.core.integer.arithmetic.IntNegation
import io.github.gciatto.ktconstraints.core.integer.arithmetic.IntProduct
import io.github.gciatto.ktconstraints.core.integer.arithmetic.IntSum
import io.github.gciatto.ktconstraints.core.integer.arithmetic.LinearSum
import io.github.gciatto.ktconstraints.core.integer.relation.IntEquality
import io.github.gciatto.ktconstraints.core.integer.relation.IntLessOrEqual
import io.github.gciatto.ktconstraints.core.integer.relation.IntLessThan
import io.github.gciatto.ktconstraints.core.real.RealExpr
import io.github.gciatto.ktconstraints.core.real.RealLiteral
import io.github.gciatto.ktconstraints.core.real.RealVar
import io.github.gciatto.ktconstraints.core.real.arithmetic.RealDifference
import io.github.gciatto.ktconstraints.core.real.arithmetic.RealNegation
import io.github.gciatto.ktconstraints.core.real.arithmetic.RealScale
import io.github.gciatto.ktconstraints.core.real.arithmetic.RealSum
import io.github.gciatto.ktconstraints.core.real.relation.RealEquality
import io.github.gciatto.ktconstraints.core.real.relation.RealLessOrEqual
import io.github.gciatto.ktconstraints.core.real.relation.RealLessThan
import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.model.isReal

/** One walk of a model into columns, rows and objective; [unsupported] lists every construct outside the fragment. */
internal class LinearTranslation(
    private val model: ConstraintModel,
) {
    val unsupported = linkedSetOf<String>()
    private val index: Map<VariableId, Int> = model.variables.withIndex().associate { (i, v) -> v.id to i }
    private val columns: List<LinearColumn?> = model.variables.map { column(it) }
    private val rows = mutableListOf<LinearRow>()
    private val objective: LinearObjective? =
        model.objective?.let { goal ->
            affine(goal.expression)?.let { LinearObjective(it.terms, it.constant, goal.maximize, goal.isReal) }
        }

    init {
        model.constraints.forEach { constraint(it) }
    }

    fun program(): LinearProgram = LinearProgram(columns.map { requireNotNull(it) }, rows.toList(), objective)

    private fun column(variable: Variable): LinearColumn? =
        when (variable) {
            is BoolVar -> LinearColumn(variable, integer = true, lower = Rational.ZERO, upper = Rational.ONE)
            is RealVar -> LinearColumn(variable, integer = false, lower = null, upper = null)
            is IntVar -> {
                val intervals = variable.domain.intervals
                when {
                    intervals.isEmpty() -> LinearColumn(variable, integer = true, lower = Rational.ONE, upper = Rational.ZERO)
                    intervals.size > 1 -> null.also { unsupported += "Integer domain with holes: ${variable.name}" }
                    else -> LinearColumn(variable, true, Rational.of(intervals.single().min), Rational.of(intervals.single().max))
                }
            }
            else -> null.also { unsupported += "Unsupported variable sort: ${variable.name}" }
        }

    /** Adds the rows of a top-level [constraint], descending through conjunctions. */
    private fun constraint(constraint: BoolExpr) {
        when (constraint) {
            is BoolAnd -> {
                constraint(constraint.left)
                constraint(constraint.right)
            }
            is BoolLiteral -> if (!constraint.value) row(Affine.ZERO, LinearRelation.LESS_OR_EQUAL, Affine.of(Rational.of(-1)))
            is BoolVar -> row(column(constraint), LinearRelation.EQUAL, Affine.of(Rational.ONE))
            is BoolNot ->
                if (constraint.operand is BoolVar) {
                    row(column(constraint.operand as BoolVar), LinearRelation.EQUAL, Affine.ZERO)
                } else {
                    unsupported += "Negated relation: ${name(constraint.operand)}"
                }
            is IntEquality -> integerRow(constraint.left, LinearRelation.EQUAL, constraint.right)
            is IntLessOrEqual -> integerRow(constraint.left, LinearRelation.LESS_OR_EQUAL, constraint.right)
            // Integer terms take integer values, so a strict inequality is exactly `left ≤ right − 1`.
            is IntLessThan -> integerRow(constraint.left, LinearRelation.LESS_OR_EQUAL, IntSum(constraint.right, IntLiteral(-1)))
            is RealEquality -> realRow(constraint.left, LinearRelation.EQUAL, constraint.right)
            is RealLessOrEqual -> realRow(constraint.left, LinearRelation.LESS_OR_EQUAL, constraint.right)
            is RealLessThan -> realRow(constraint.left, LinearRelation.LESS, constraint.right)
            else -> unsupported += "Non-linear constraint: ${name(constraint)}"
        }
    }

    private fun integerRow(
        left: IntExpr,
        relation: LinearRelation,
        right: IntExpr,
    ) = row(affine(left), relation, affine(right))

    private fun realRow(
        left: RealExpr,
        relation: LinearRelation,
        right: RealExpr,
    ) = row(affine(left), relation, affine(right))

    private fun row(
        left: Affine?,
        relation: LinearRelation,
        right: Affine?,
    ) {
        if (left == null || right == null) return
        val difference = left - right
        rows += LinearRow(difference.terms, relation, -difference.constant)
    }

    private fun column(variable: BoolVar): Affine = Affine.of(requireNotNull(index[variable.id]) { "Foreign variable ${variable.name}" })

    /** The exact affine form of an integer or real [expression], or null after reporting why it is not linear. */
    private fun affine(expression: Expr): Affine? =
        when (expression) {
            is IntLiteral -> Affine.of(Rational.of(expression.value))
            is RealLiteral -> Affine.of(expression.value)
            is IntVar, is RealVar -> Affine.of(requireNotNull(index[expression.id]))
            is LinearSum -> linearSum(expression)
            is IntSum -> both(expression.left, expression.right) { l, r -> l + r }
            is RealSum -> both(expression.left, expression.right) { l, r -> l + r }
            is IntDifference -> both(expression.left, expression.right) { l, r -> l - r }
            is RealDifference -> both(expression.left, expression.right) { l, r -> l - r }
            is IntNegation -> affine(expression.operand)?.times(Rational.of(-1))
            is RealNegation -> affine(expression.operand)?.times(Rational.of(-1))
            is RealScale -> affine(expression.operand)?.times(expression.coefficient)
            is IntToReal -> affine(expression.operand)
            is IntProduct -> product(expression)
            is BoolToInt -> indicator(expression.operand)
            else -> null.also { unsupported += "Non-linear expression: ${name(expression)}" }
        }

    private fun linearSum(expression: LinearSum): Affine? {
        val terms = expression.terms.map { affine(it) }
        if (terms.any { it == null }) return null
        return terms.zip(expression.coefficients).fold(Affine.of(Rational.of(expression.constant))) { total, (term, c) ->
            total + requireNotNull(term) * Rational.of(c)
        }
    }

    private fun product(expression: IntProduct): Affine? {
        val left = affine(expression.left) ?: return null
        val right = affine(expression.right) ?: return null
        return when {
            left.terms.isEmpty() -> right * left.constant
            right.terms.isEmpty() -> left * right.constant
            else -> null.also { unsupported += "Product of variables: ${name(expression)}" }
        }
    }

    /** `[b]` for a Boolean variable `b`, or `1 − [b]` for its negation; other reified formulas are not linear. */
    private fun indicator(operand: BoolExpr): Affine? =
        when {
            operand is BoolVar -> column(operand)
            operand is BoolNot && operand.operand is BoolVar -> Affine.of(Rational.ONE) - column(operand.operand as BoolVar)
            operand is BoolLiteral -> Affine.of(if (operand.value) Rational.ONE else Rational.ZERO)
            else -> null.also { unsupported += "Reified relation: ${name(operand)}" }
        }

    private inline fun both(
        left: Expr,
        right: Expr,
        combine: (Affine, Affine) -> Affine,
    ): Affine? {
        val l = affine(left)
        val r = affine(right)
        return if (l == null || r == null) null else combine(l, r)
    }

    private fun name(expression: Expr): String = expression::class.simpleName ?: expression.toString()
}
