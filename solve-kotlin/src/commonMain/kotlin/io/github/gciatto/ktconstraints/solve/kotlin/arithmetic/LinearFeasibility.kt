package io.github.gciatto.ktconstraints.solve.kotlin.arithmetic

import io.github.gciatto.ktconstraints.core.Rational
import io.github.gciatto.ktconstraints.solve.kotlin.lp.Certificates
import io.github.gciatto.ktconstraints.solve.kotlin.lp.DeltaRational
import io.github.gciatto.ktconstraints.solve.kotlin.lp.Simplex

/**
 * Exact feasibility of conjunctions of linear inequalities over the reals, for the SAT engine's real theory.
 *
 * Each distinct left-hand side, up to a scale, gets one simplex row whose slack carries the tightest bound asserted on
 * either side. The tableau persists between calls, so successive checks over the same atoms warm-start from the last
 * basis; new left-hand sides rebuild it. Infeasibility comes with the indices of the inequalities in a Farkas
 * refutation, which is checked before it is returned. [checkpoint] runs before every pivot and may throw to stop.
 */
internal class LinearFeasibility(
    private val checkpoint: () -> Unit = {},
) {
    private var variables = -1
    private val slacks = HashMap<Map<Int, Rational>, Int>()
    private var simplex: Simplex? = null

    /** Decides the conjunction over [variableCount] real variables. */
    fun solve(
        variableCount: Int,
        constraints: List<LinearInequality>,
    ): LinearResult {
        require(variableCount >= 0) { "The variable count must not be negative" }
        constraints.forEachIndexed { index, row ->
            require(row.coefficients.keys.all { it in 0 until variableCount }) { "Linear variable outside the declared universe" }
            if (row.coefficients.isEmpty() && !holds(row)) return LinearResult(null, listOf(index))
        }
        val scaled = constraints.map { if (it.coefficients.isEmpty()) null else Scaled(it) }
        val tableau = tableau(variableCount, scaled)
        val lower = HashMap<Int, Int>()
        val upper = HashMap<Int, Int>()
        scaled.forEachIndexed { index, row ->
            if (row == null) return@forEachIndexed
            val slack = slacks.getValue(row.form)
            val side = if (row.upper) upper else lower
            val previous = side[slack]
            if (previous == null || row.tighter(scaled[previous]!!)) side[slack] = index
        }
        for ((form, slack) in slacks) {
            tableau.bound(slack, lower[slack]?.let { scaled[it]!!.bound }, upper[slack]?.let { scaled[it]!!.bound })
        }
        if (!tableau.check()) {
            val facts =
                tableau.conflict.map { use ->
                    val index = requireNotNull((if (use.upper) upper else lower)[use.index]) { "A conflict used an unasserted bound" }
                    index to use.multiplier / scaled[index]!!.scale
                }
            check(Certificates.refutes(facts.map { (index, multiplier) -> constraints[index] to multiplier })) {
                "The simplex produced an invalid Farkas certificate"
            }
            return LinearResult(null, facts.map { it.first }.distinct().sorted())
        }
        val epsilon = tableau.epsilon()
        return LinearResult(List(variableCount) { tableau.valueOf(it).at(epsilon) })
    }

    private fun tableau(
        variableCount: Int,
        scaled: List<Scaled?>,
    ): Simplex {
        val known = simplex
        if (known != null && variableCount == variables && scaled.all { it == null || it.form in slacks }) return known
        variables = variableCount
        val forms = (slacks.keys + scaled.mapNotNull { it?.form }).distinct()
        slacks.clear()
        forms.forEachIndexed { r, form -> slacks[form] = variableCount + r }
        return Simplex(variableCount, forms, stopped = {
            checkpoint()
            false
        }).also { simplex = it }
    }

    private fun holds(row: LinearInequality): Boolean = row.upperBound > Rational.ZERO || row.upperBound == Rational.ZERO && !row.strict

    /** [row] divided by its first coefficient's magnitude [scale]: `form ≤ bound` when [upper], else `form ≥ bound`. */
    private class Scaled(
        row: LinearInequality,
    ) {
        private val first = row.coefficients.values.first()
        val scale: Rational = if (first < Rational.ZERO) -first else first
        val upper = first > Rational.ZERO
        private val sign = if (upper) Rational.ONE else Rational.of(-1)

        /** The left-hand side, normalized so that its first coefficient is 1. */
        val form: Map<Int, Rational> = row.coefficients.mapValues { it.value * sign / scale }

        /** The slack's bound; a strict row moves it by one δ inwards. */
        val bound: DeltaRational =
            DeltaRational.of(row.upperBound * sign / scale).let {
                if (!row.strict) it else it + DeltaRational(Rational.ZERO, if (upper) Rational.of(-1) else Rational.ONE)
            }

        fun tighter(other: Scaled): Boolean = if (upper) bound < other.bound else bound > other.bound
    }
}
