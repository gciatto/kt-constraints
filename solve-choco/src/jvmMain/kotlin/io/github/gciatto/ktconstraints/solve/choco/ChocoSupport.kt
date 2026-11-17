package io.github.gciatto.ktconstraints.solve.choco

import io.github.gciatto.ktconstraints.core.AllDifferent
import io.github.gciatto.ktconstraints.core.BoolAnd
import io.github.gciatto.ktconstraints.core.BoolCardinality
import io.github.gciatto.ktconstraints.core.BoolEquality
import io.github.gciatto.ktconstraints.core.BoolExpr
import io.github.gciatto.ktconstraints.core.BoolImplies
import io.github.gciatto.ktconstraints.core.BoolLiteral
import io.github.gciatto.ktconstraints.core.BoolNot
import io.github.gciatto.ktconstraints.core.BoolOr
import io.github.gciatto.ktconstraints.core.BoolVar
import io.github.gciatto.ktconstraints.core.Element
import io.github.gciatto.ktconstraints.core.Expr
import io.github.gciatto.ktconstraints.core.IntDifference
import io.github.gciatto.ktconstraints.core.IntEquality
import io.github.gciatto.ktconstraints.core.IntExpr
import io.github.gciatto.ktconstraints.core.IntLessOrEqual
import io.github.gciatto.ktconstraints.core.IntLessThan
import io.github.gciatto.ktconstraints.core.IntLiteral
import io.github.gciatto.ktconstraints.core.IntNegation
import io.github.gciatto.ktconstraints.core.IntProduct
import io.github.gciatto.ktconstraints.core.IntSum
import io.github.gciatto.ktconstraints.core.IntVar
import io.github.gciatto.ktconstraints.core.LinearSum
import io.github.gciatto.ktconstraints.core.Table
import io.github.gciatto.ktconstraints.core.fold
import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.model.Objective
import io.github.gciatto.ktconstraints.solve.spi.SupportReport
import org.gciatto.kt.math.BigInteger

/** Assessment of the Boolean/integer subset Choco represents exactly within its 32-bit variable bounds. */
internal object ChocoSupport {
    /** Bound on every value and accumulated magnitude, so `lhs - rhs` of any relation fits `IntVar.MAX_INT_BOUND`. */
    val LIMIT: BigInteger = BigInteger.of(LIMIT_VALUE)

    private const val LIMIT_VALUE = 10_000_000L

    fun report(model: ConstraintModel): SupportReport = Assessment().apply { assess(model) }.report()

    /** Exact range of a supported integer expression; empty domains are represented by zero. */
    fun bounds(expression: IntExpr): ChocoBounds = expression.fold { node, children: List<ChocoBounds> -> combine(node, children) }

    private fun combine(
        node: Expr,
        children: List<ChocoBounds>,
    ): ChocoBounds =
        when (node) {
            is IntVar -> {
                val domain = node.domain
                val intervals = domain.intervals
                if (intervals.isEmpty()) {
                    ChocoBounds(
                        BigInteger.ZERO,
                        BigInteger.ZERO,
                    )
                } else {
                    ChocoBounds(intervals.first().min, intervals.last().max)
                }
            }
            is IntLiteral -> ChocoBounds(node.value, node.value)
            is IntSum -> sum(children[0], children[1])
            is IntDifference -> sum(children[0], negate(children[1]))
            is IntNegation -> negate(children.single())
            is LinearSum ->
                children.zip(node.coefficients).fold(ChocoBounds(node.constant, node.constant)) { total, (term, coefficient) ->
                    sum(total, scale(term, coefficient))
                }
            is IntProduct -> product(children[0], children[1])
            is Element -> ChocoBounds(children.drop(1).minOf { it.min }, children.drop(1).maxOf { it.max })
            else -> throw IllegalArgumentException("Unsupported integer expression: $node")
        }

    private fun sum(
        left: ChocoBounds,
        right: ChocoBounds,
    ) = ChocoBounds(left.min + right.min, left.max + right.max, left.magnitude + right.magnitude)

    private fun negate(bounds: ChocoBounds) = ChocoBounds(-bounds.max, -bounds.min, bounds.magnitude)

    private fun scale(
        bounds: ChocoBounds,
        coefficient: BigInteger,
    ): ChocoBounds {
        val scaled = ChocoBounds(bounds.min * coefficient, bounds.max * coefficient, bounds.magnitude * coefficient.absoluteValue)
        return if (coefficient.signum < 0) ChocoBounds(scaled.max, scaled.min, scaled.magnitude) else scaled
    }

    private fun product(
        left: ChocoBounds,
        right: ChocoBounds,
    ): ChocoBounds {
        val corners = listOf(left.min * right.min, left.min * right.max, left.max * right.min, left.max * right.max)
        return ChocoBounds(corners.minOf { it }, corners.maxOf { it })
    }

    /** Collects the unsupported constructs of one model. */
    private class Assessment {
        private val unsupported = linkedSetOf<String>()

        fun report() = SupportReport(unsupported)

        fun assess(model: ConstraintModel) {
            for (variable in model.variables) {
                when (variable) {
                    is BoolVar -> Unit
                    is IntVar -> integer(variable)
                    else -> unsupported += "Unsupported variable sort: ${variable.name}"
                }
            }
            model.constraints.forEach { boolean(it) }
            when (val objective = model.objective) {
                is Objective.Minimize -> integer(objective.expression)
                is Objective.Maximize -> integer(objective.expression)
                null -> Unit
            }
        }

        private fun boolean(expression: BoolExpr) {
            when (expression) {
                is BoolVar, is BoolLiteral -> Unit
                is BoolAnd, is BoolOr, is BoolImplies, is BoolEquality, is BoolNot, is BoolCardinality ->
                    expression.children.forEach { boolean(it as BoolExpr) }
                is IntEquality, is IntLessThan, is IntLessOrEqual -> expression.children.forEach { integer(it as IntExpr) }
                is AllDifferent, is Table -> global(expression)
                else -> unsupported += "Unsupported expression: ${expression::class.simpleName ?: expression}"
            }
        }

        private fun global(expression: BoolExpr) {
            expression.children.forEach { integer(it as IntExpr) }
            if (expression is Table) expression.tuples.flatten().forEach { check(it, "Table value") }
        }

        private fun integer(expression: IntExpr) {
            val before = unsupported.size
            expression.fold { node, _: List<Unit> ->
                when (node) {
                    is IntVar, is IntLiteral, is IntSum, is IntDifference, is IntNegation, is IntProduct, is Element -> Unit
                    is LinearSum -> {
                        node.coefficients.forEach { check(it, "Coefficient") }
                        check(node.constant, "Constant")
                    }
                    else -> unsupported += "Unsupported expression: ${node::class.simpleName ?: node}"
                }
            }
            if (unsupported.size != before) return
            expression.fold { node, children: List<ChocoBounds> ->
                combine(node, children).also {
                    if (!it.fits) unsupported += "Integer range of $node exceeds the Choco margin of 10^7"
                }
            }
        }

        private fun check(
            value: BigInteger,
            what: String,
        ) {
            if (value.absoluteValue > LIMIT) unsupported += "$what $value exceeds the Choco margin of 10^7"
        }
    }
}
