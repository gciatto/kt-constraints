package io.github.gciatto.ktconstraints.solve.kotlin.lp

import io.github.gciatto.ktconstraints.core.Rational
import io.github.gciatto.ktconstraints.solve.linear.LinearProgram
import io.github.gciatto.ktconstraints.solve.linear.LinearRelation

/**
 * Exact bounded-variable simplex over a [LinearProgram]'s continuous relaxation (integrality is ignored). Columns are
 * variables `0 until n`; row `r` gets the slack variable `n + r`, equal to its left-hand side and bounded by its
 * right-hand side. Values are [DeltaRational]s, so strict rows hold exactly. [check] is the general simplex of
 * Dutertre and de Moura, [maximize] the primal simplex with bound flips; both use Bland's rule, so neither cycles.
 * Bounds can be tightened between calls, which is how branch and bound reuses one tableau.
 */
internal class Simplex(
    program: LinearProgram,
    /** Polled before every pivot: true stops the search with [SimplexStopped]. */
    private val stopped: () -> Boolean = { false },
) {
    private val columns = program.columns.size
    private val size = columns + program.rows.size
    private val lower = arrayOfNulls<DeltaRational>(size)
    private val upper = arrayOfNulls<DeltaRational>(size)
    private val value = Array(size) { DeltaRational.ZERO }

    /** The tableau: each basic variable as a combination of nonbasic ones. */
    private val rows = HashMap<Int, MutableMap<Int, Rational>>()

    init {
        program.columns.forEachIndexed { i, column ->
            lower[i] = column.lower?.let { DeltaRational.of(it) }
            upper[i] = column.upper?.let { DeltaRational.of(it) }
        }
        program.rows.forEachIndexed { r, row ->
            val slack = columns + r
            rows[slack] = row.terms.toMutableMap()
            val rhs = DeltaRational.of(row.rhs)
            when (row.relation) {
                LinearRelation.LESS_OR_EQUAL -> upper[slack] = rhs
                LinearRelation.LESS -> upper[slack] = rhs - DeltaRational(Rational.ZERO, Rational.ONE)
                LinearRelation.EQUAL -> {
                    lower[slack] = rhs
                    upper[slack] = rhs
                }
            }
        }
        for (i in 0 until columns) value[i] = clamp(i, DeltaRational.ZERO)
        rows.forEach { (basic, row) -> value[basic] = evaluate(row) }
    }

    /** The current value of column [index]. */
    fun valueOf(index: Int): DeltaRational = value[index]

    /** Lower bound of column [index], or null. */
    fun lowerOf(index: Int): DeltaRational? = lower[index]

    /** Upper bound of column [index], or null. */
    fun upperOf(index: Int): DeltaRational? = upper[index]

    /** Tightens column [index]'s bounds; [check] restores feasibility, or proves there is none. */
    fun bound(
        index: Int,
        lower: DeltaRational?,
        upper: DeltaRational?,
    ) {
        this.lower[index] = lower
        this.upper[index] = upper
        if (index !in rows) update(index, clamp(index, value[index]))
    }

    /** Pivots until every bound holds; false when the bounds and rows are infeasible. */
    fun check(): Boolean {
        while (true) {
            if (stopped()) throw SimplexStopped()
            val violated = rows.keys.filter { violates(it) }.minOrNull() ?: return true
            val row = rows.getValue(violated)
            val raise = lower[violated]?.let { value[violated] < it } == true
            val entering =
                row.entries
                    .filter { (j, a) -> if (raise == a > Rational.ZERO) canIncrease(j) else canDecrease(j) }
                    .minOfOrNull { it.key } ?: return false
            pivotAndUpdate(violated, entering, requireNotNull(if (raise) lower[violated] else upper[violated]))
        }
    }

    /**
     * Maximizes `Σ objective[j]·x[j]` from a feasible state ([check] must have succeeded): returns false when the
     * objective is unbounded, true when the current state is optimal.
     */
    fun maximize(objective: Map<Int, Rational>): Boolean {
        while (true) {
            if (stopped()) throw SimplexStopped()
            val costs = reducedCosts(objective)
            val entering =
                costs.entries
                    .filter { (j, c) -> if (c > Rational.ZERO) canIncrease(j) else canDecrease(j) }
                    .minOfOrNull { it.key } ?: return true
            val increase = costs.getValue(entering) > Rational.ZERO
            val step = step(entering, increase) ?: return false
            when (val leaving = step.leaving) {
                null -> update(entering, value[entering] + if (increase) step.amount else DeltaRational.ZERO - step.amount)
                else -> pivotAndUpdate(leaving, entering, requireNotNull(step.target))
            }
        }
    }

    /** The objective's value in the current state. */
    fun objectiveValue(objective: Map<Int, Rational>): DeltaRational =
        objective.entries.fold(DeltaRational.ZERO) { total, (j, c) -> total + value[j] * c }

    /**
     * A positive rational small enough for δ that every bound of the current state still holds, so that
     * `value.at(epsilon)` is an exact solution of the original strict rows.
     */
    fun epsilon(): Rational {
        var epsilon = Rational.ONE
        for (i in 0 until size) {
            lower[i]?.let { epsilon = tighten(epsilon, it, value[i]) }
            upper[i]?.let { epsilon = tighten(epsilon, value[i], it) }
        }
        return epsilon
    }

    /** The largest movement of [entering] in its improving direction, and the basic variable that would leave. */
    private fun step(
        entering: Int,
        increase: Boolean,
    ): Step? {
        var best: Step? =
            (if (increase) upper[entering]?.let { it - value[entering] } else lower[entering]?.let { value[entering] - it })?.let {
                Step(it, null, null)
            }
        for (basic in rows.keys.sorted()) {
            val a = rows.getValue(basic)[entering] ?: continue
            val rising = (a > Rational.ZERO) == increase
            val bound = (if (rising) upper[basic] else lower[basic]) ?: continue
            val room = if (rising) bound - value[basic] else value[basic] - bound
            val amount = room / abs(a)
            val current = best
            if (current == null || amount < current.amount) best = Step(amount, basic, bound)
        }
        return best
    }

    private fun reducedCosts(objective: Map<Int, Rational>): Map<Int, Rational> {
        val costs = HashMap<Int, Rational>()
        objective.forEach { (j, c) ->
            val row = rows[j]
            if (row == null) {
                costs.add(j, c)
            } else {
                row.forEach { (k, a) -> costs.add(k, c * a) }
            }
        }
        return costs.filterValues { it != Rational.ZERO }
    }

    /** Sets basic [leaving] to [target] by moving nonbasic [entering], then swaps their roles. */
    private fun pivotAndUpdate(
        leaving: Int,
        entering: Int,
        target: DeltaRational,
    ) {
        val coefficient = rows.getValue(leaving).getValue(entering)
        val theta = (target - value[leaving]) / coefficient
        value[leaving] = target
        value[entering] = value[entering] + theta
        rows.forEach { (basic, row) -> if (basic != leaving) row[entering]?.let { value[basic] = value[basic] + theta * it } }
        pivot(leaving, entering)
    }

    private fun pivot(
        leaving: Int,
        entering: Int,
    ) {
        val old = rows.remove(leaving)!!
        val coefficient = old.remove(entering)!!
        val inverse = Rational.ONE / coefficient
        val replacement = HashMap<Int, Rational>()
        replacement[leaving] = inverse
        old.forEach { (k, a) -> replacement[k] = -(a * inverse) }
        rows.values.forEach { row ->
            val factor = row.remove(entering) ?: return@forEach
            replacement.forEach { (k, a) -> row.add(k, factor * a) }
        }
        rows[entering] = replacement
    }

    /** Moves nonbasic [index] to [target], updating the basic variables that depend on it. */
    private fun update(
        index: Int,
        target: DeltaRational,
    ) {
        val change = target - value[index]
        if (change == DeltaRational.ZERO) return
        value[index] = target
        rows.forEach { (basic, row) -> row[index]?.let { value[basic] = value[basic] + change * it } }
    }

    private fun evaluate(row: Map<Int, Rational>): DeltaRational =
        row.entries.fold(DeltaRational.ZERO) { total, (j, a) ->
            total +
                value[j] * a
        }

    private fun clamp(
        index: Int,
        target: DeltaRational,
    ): DeltaRational {
        lower[index]?.let { if (target < it) return it }
        upper[index]?.let { if (target > it) return it }
        return target
    }

    private fun violates(index: Int): Boolean =
        lower[index]?.let { value[index] < it } == true || upper[index]?.let { value[index] > it } == true

    private fun canIncrease(index: Int): Boolean = upper[index]?.let { value[index] < it } ?: true

    private fun canDecrease(index: Int): Boolean = lower[index]?.let { value[index] > it } ?: true

    /** Keeps `low ≤ high` true at δ = [epsilon], halving the room left by their real parts when δ must shrink. */
    private fun tighten(
        epsilon: Rational,
        low: DeltaRational,
        high: DeltaRational,
    ): Rational {
        val gap = high.real - low.real
        val slope = low.delta - high.delta
        if (slope <= Rational.ZERO || gap <= Rational.ZERO) return epsilon
        val limit = gap / slope / Rational.of(2)
        return if (limit < epsilon) limit else epsilon
    }

    private fun MutableMap<Int, Rational>.add(
        key: Int,
        amount: Rational,
    ) {
        val total = (this[key] ?: Rational.ZERO) + amount
        if (total == Rational.ZERO) remove(key) else this[key] = total
    }

    private fun abs(value: Rational): Rational = if (value < Rational.ZERO) -value else value

    /** A primal step: how far the entering variable moves, and the basic variable reaching [target] first, if any. */
    private class Step(
        val amount: DeltaRational,
        val leaving: Int?,
        val target: DeltaRational?,
    )
}
