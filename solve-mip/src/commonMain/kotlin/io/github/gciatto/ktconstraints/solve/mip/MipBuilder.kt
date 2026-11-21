package io.github.gciatto.ktconstraints.solve.mip

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
import io.github.gciatto.ktconstraints.core.CardinalityRelation
import io.github.gciatto.ktconstraints.core.DecomposableConstraint
import io.github.gciatto.ktconstraints.core.Element
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
import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.model.Objective
import org.gciatto.kt.math.BigInteger

/**
 * Lowers one model eagerly. Boolean expressions become affine 0/1 forms ("literals"); every auxiliary binary is defined
 * in both directions from exact bounds, so it is determined by the source variables. Rejections are collected per
 * constraint so the report names every unsupported part.
 */
internal class MipBuilder(
    model: ConstraintModel,
) {
    val unsupported = linkedSetOf<String>()
    val decompositions = linkedSetOf<String>()
    private val variables = mutableListOf<MipVariable>()
    private val rows = mutableListOf<MipRow>()
    private val booleans = linkedMapOf<BoolVar, Int>()
    private val integers = linkedMapOf<IntVar, Int>()
    private var objective: MipObjective? = null

    init {
        attempt { declare(model) }
        model.constraints.forEach { attempt { post(it) } }
        attempt {
            objective =
                when (val goal = model.objective) {
                    is Objective.Minimize -> linear(goal.expression).let { MipObjective(it.terms, it.constant, maximize = false) }
                    is Objective.Maximize -> linear(goal.expression).let { MipObjective(it.terms, it.constant, maximize = true) }
                    null -> null
                }
        }
    }

    fun program(): MipProgram = MipProgram(variables.toList(), rows.toList(), objective, booleans, integers)

    private fun attempt(block: () -> Unit) {
        try {
            block()
        } catch (rejection: Rejection) {
            unsupported += rejection.reason
        }
    }

    private fun declare(model: ConstraintModel) {
        for (variable in model.variables) {
            when (variable) {
                is BoolVar -> booleans[variable] = binary()
                is IntVar -> integers[variable] = integer(variable)
                else -> throw Rejection("Unsupported variable sort: ${variable.name}")
            }
        }
    }

    private fun integer(variable: IntVar): Int {
        val intervals = variable.domain.intervals
        if (intervals.isEmpty()) {
            require(MipAffine.ZERO, BigInteger.ONE, BigInteger.ONE)
            return fresh(BigInteger.ZERO, BigInteger.ZERO)
        }
        intervals.forEach {
            check(it.min, "Domain bound")
            check(it.max, "Domain bound")
        }
        val index = fresh(intervals.first().min, intervals.last().max)
        if (intervals.size > 1) {
            decompositions += MipLowering.HOLES
            val selectors = intervals.map { binary() }
            require(selectors.indexSum(), BigInteger.ONE, BigInteger.ONE)
            val x = MipAffine.of(index)
            require(x - intervals.zip(selectors).sumOf { (interval, z) -> MipAffine.of(z) * interval.min }, BigInteger.ZERO, null)
            require(x - intervals.zip(selectors).sumOf { (interval, z) -> MipAffine.of(z) * interval.max }, null, BigInteger.ZERO)
        }
        return index
    }

    private fun post(expression: BoolExpr) {
        when (expression) {
            is BoolAnd -> {
                post(expression.left)
                post(expression.right)
            }
            is IntEquality -> difference(expression.left, expression.right).let { require(it, BigInteger.ZERO, BigInteger.ZERO) }
            is IntLessOrEqual -> require(difference(expression.left, expression.right), null, BigInteger.ZERO)
            is IntLessThan -> require(difference(expression.left, expression.right), null, MipAffine.MINUS_ONE)
            is DecomposableConstraint -> post(expression.decomposition.also { decompositions += MipLowering.STRUCTURAL })
            else -> require(literal(expression), BigInteger.ONE, BigInteger.ONE)
        }
    }

    /** An affine 0/1 form equal to the truth value of [expression]. */
    private fun literal(expression: BoolExpr): MipAffine =
        when (expression) {
            is BoolVar -> MipAffine.of(booleans.getValue(expression))
            is BoolLiteral -> if (expression.value) MipAffine.ONE else MipAffine.ZERO
            is BoolNot -> negation(literal(expression.operand))
            is BoolAnd -> conjunction(listOf(literal(expression.left), literal(expression.right)))
            is BoolOr -> disjunction(listOf(literal(expression.left), literal(expression.right)))
            is BoolImplies -> disjunction(listOf(negation(literal(expression.left)), literal(expression.right)))
            is BoolEquality -> zero(literal(expression.left) - literal(expression.right))
            is IntEquality -> zero(difference(expression.left, expression.right))
            is IntLessOrEqual -> nonPositive(difference(expression.left, expression.right))
            is IntLessThan -> nonPositive(difference(expression.left, expression.right) + MipAffine.ONE)
            is BoolCardinality -> cardinality(expression)
            is AllDifferent -> allDifferent(expression)
            is Table -> table(expression)
            is DecomposableConstraint -> literal(expression.decomposition.also { decompositions += MipLowering.STRUCTURAL })
            else -> throw Rejection("Unsupported expression: ${expression::class.simpleName ?: expression}")
        }

    private fun cardinality(expression: BoolCardinality): MipAffine {
        val surplus = expression.operands.map { literal(it) }.sum() - MipAffine.of(BigInteger.of(expression.count.toLong()))
        return when (expression.relation) {
            CardinalityRelation.AT_MOST -> nonPositive(surplus)
            CardinalityRelation.AT_LEAST -> nonPositive(surplus * MipAffine.MINUS_ONE)
            CardinalityRelation.EXACTLY -> zero(surplus)
        }
    }

    private fun allDifferent(expression: AllDifferent): MipAffine {
        decompositions += MipLowering.ALL_DIFFERENT
        val operands = expression.operands.map { linear(it) }
        val pairs = operands.indices.flatMap { i -> (i + 1 until operands.size).map { j -> operands[i] to operands[j] } }
        return conjunction(pairs.map { (left, right) -> negation(zero(left - right)) })
    }

    private fun table(expression: Table): MipAffine {
        decompositions += MipLowering.TABLE
        expression.tuples.flatten().forEach { check(it, "Table value") }
        val operands = expression.operands.map { linear(it) }
        return disjunction(
            expression.tuples.map { row -> conjunction(operands.zip(row).map { (operand, value) -> zero(operand - MipAffine.of(value)) }) },
        )
    }

    private fun negation(literal: MipAffine): MipAffine = MipAffine.ONE - literal

    /** A binary `r` with `r ≤ lᵢ` and `r ≥ Σ lᵢ − (n − 1)`; constants are folded first. */
    private fun conjunction(literals: List<MipAffine>): MipAffine {
        if (literals.any { it == MipAffine.ZERO }) return MipAffine.ZERO
        val open = literals.filter { it != MipAffine.ONE }.distinct()
        if (open.size <= 1) return open.singleOrNull() ?: MipAffine.ONE
        decompositions += MipLowering.REIFICATION
        val r = MipAffine.of(binary())
        open.forEach { require(r - it, null, BigInteger.ZERO) }
        require(r - open.sum(), BigInteger.of(1L - open.size), null)
        return r
    }

    private fun disjunction(literals: List<MipAffine>): MipAffine = negation(conjunction(literals.map { negation(it) }))

    private fun zero(form: MipAffine): MipAffine = conjunction(listOf(nonPositive(form), nonPositive(form * MipAffine.MINUS_ONE)))

    /** A binary `r ⇔ form ≤ 0` over `form ∈ [lo, hi]`: `form + hi·r ≤ hi` and `form − (lo − 1)·r ≥ 1`. */
    private fun nonPositive(form: MipAffine): MipAffine {
        val lo = form.min(variables)
        val hi = form.max(variables)
        return when {
            hi.signum <= 0 -> MipAffine.ONE
            lo.signum > 0 -> MipAffine.ZERO
            else -> {
                decompositions += MipLowering.REIFICATION
                val r = binary()
                require(form + MipAffine.of(r) * hi, null, hi)
                require(form - MipAffine.of(r) * (lo - BigInteger.ONE), BigInteger.ONE, null)
                MipAffine.of(r)
            }
        }
    }

    private fun difference(
        left: IntExpr,
        right: IntExpr,
    ): MipAffine = linear(left) - linear(right)

    private fun linear(expression: IntExpr): MipAffine {
        val form =
            when (expression) {
                is IntVar -> MipAffine.of(integers.getValue(expression))
                is IntLiteral -> MipAffine.of(check(expression.value, "Literal"))
                is IntSum -> linear(expression.left) + linear(expression.right)
                is IntDifference -> linear(expression.left) - linear(expression.right)
                is IntNegation -> linear(expression.operand) * MipAffine.MINUS_ONE
                is LinearSum ->
                    MipAffine.sum(
                        expression.terms.zip(expression.coefficients).map { (term, c) -> linear(term) * check(c, "Coefficient") } +
                            MipAffine.of(check(expression.constant, "Constant")),
                    )
                is IntProduct -> product(linear(expression.left), linear(expression.right))
                is Element -> element(expression)
                is BoolToInt -> literal(expression.operand)
                is IntMin -> extremum(expression.operands, lowest = true)
                is IntMax -> extremum(expression.operands, lowest = false)
                else -> throw Rejection("Unsupported expression: ${expression::class.simpleName ?: expression}")
            }
        if (form.magnitude(variables) > MipLowering.LIMIT) throw Rejection("Integer range of $expression exceeds the MIP margin of 10^6")
        return form
    }

    private fun product(
        left: MipAffine,
        right: MipAffine,
    ): MipAffine =
        when {
            left.terms.isEmpty() -> right * left.constant
            right.terms.isEmpty() -> left * right.constant
            else -> throw Rejection("Products need a fixed factor on MIP routes")
        }

    /** Top-level one-hot selection of the index; the value is linked to the selected operand by exact big-M rows. */
    private fun element(expression: Element): MipAffine {
        decompositions += MipLowering.ELEMENT
        val index = linear(expression.index)
        val values = expression.values.map { linear(it) }
        val first = index.min(variables).max(BigInteger.ZERO)
        val last = index.max(variables).min(BigInteger.of(values.size.toLong() - 1))
        if (first > last) {
            require(MipAffine.ZERO, BigInteger.ONE, BigInteger.ONE)
            return MipAffine.ZERO
        }
        val positions = generateSequence(first) { it + BigInteger.ONE }.takeWhile { it <= last }.toList()
        val selectors = positions.map { binary() }
        require(selectors.indexSum(), BigInteger.ONE, BigInteger.ONE)
        require(index - positions.zip(selectors).sumOf { (k, b) -> MipAffine.of(b) * k }, BigInteger.ZERO, BigInteger.ZERO)
        val chosen = positions.map { values[it.toInt()] }
        val y = MipAffine.of(fresh(chosen.minOf { it.min(variables) }, chosen.maxOf { it.max(variables) }))
        chosen.zip(selectors).forEach { (value, b) ->
            val gap = y - value
            val above = gap.max(variables)
            val below = gap.min(variables)
            require(gap + MipAffine.of(b) * above, null, above)
            require(gap + MipAffine.of(b) * below, below, null)
        }
        return y
    }

    /** Every operand bounds the result; exactly one selected operand equals it. */
    private fun extremum(
        operands: List<IntExpr>,
        lowest: Boolean,
    ): MipAffine {
        val values = operands.map { linear(it) }
        if (values.size == 1) return values.single()
        decompositions += MipLowering.MIN_MAX
        val select: (List<BigInteger>) -> BigInteger = { bounds -> if (lowest) bounds.minOf { it } else bounds.maxOf { it } }
        val y = MipAffine.of(fresh(select(values.map { it.min(variables) }), select(values.map { it.max(variables) })))
        val selectors = values.map { binary() }
        require(selectors.indexSum(), BigInteger.ONE, BigInteger.ONE)
        values.zip(selectors).forEach { (value, b) ->
            val gap = if (lowest) value - y else y - value
            val above = gap.max(variables)
            require(gap, BigInteger.ZERO, null)
            require(gap + MipAffine.of(b) * above, null, above)
        }
        return y
    }

    private fun require(
        form: MipAffine,
        min: BigInteger?,
        max: BigInteger?,
    ) {
        val low = min?.let { it - form.constant }
        val high = max?.let { it - form.constant }
        if (form.terms.isEmpty()) {
            if ((low == null || low.signum <= 0) && (high == null || high.signum >= 0)) return
        }
        rows += MipRow(form.terms, low, high)
    }

    private fun fresh(
        min: BigInteger,
        max: BigInteger,
    ): Int {
        variables += MipVariable(min, max)
        return variables.lastIndex
    }

    private fun binary(): Int = fresh(BigInteger.ZERO, BigInteger.ONE)

    private fun check(
        value: BigInteger,
        what: String,
    ): BigInteger {
        if (value.absoluteValue > MipLowering.LIMIT) throw Rejection("$what $value exceeds the MIP margin of 10^6")
        return value
    }

    private fun List<Int>.indexSum(): MipAffine = MipAffine.sum(map { MipAffine.of(it) })

    private fun List<MipAffine>.sum(): MipAffine = MipAffine.sum(this)

    private inline fun <T> List<T>.sumOf(form: (T) -> MipAffine): MipAffine = MipAffine.sum(map(form))

    /** Aborts the lowering of one constraint, objective or declaration. */
    private class Rejection(
        val reason: String,
    ) : RuntimeException(reason)
}
