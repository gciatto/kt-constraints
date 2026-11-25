package io.github.gciatto.ktconstraints.solve.kotlin.lp

import io.github.gciatto.ktconstraints.solve.linear.LinearProgram
import io.github.gciatto.ktconstraints.solve.linear.LinearRelation
import io.github.gciatto.ktconstraints.solve.linear.approximate
import kotlin.math.abs

/**
 * A dense bounded-variable simplex in doubles, laid out like [Simplex] (columns, then one slack per row), used only to
 * guess an optimal basis that [VerifiedStart] then confirms exactly. It never decides anything on its own: any
 * numerical trouble, infeasibility or unboundedness just yields no basis.
 */
internal class FloatingSimplex(
    program: LinearProgram,
    private val stopped: () -> Boolean,
) {
    private val columns = program.columns.size
    private val rowCount = program.rows.size
    private val size = columns + rowCount
    private val lower = DoubleArray(size) { Double.NEGATIVE_INFINITY }
    private val upper = DoubleArray(size) { Double.POSITIVE_INFINITY }
    private val value = DoubleArray(size)
    private val basis = IntArray(rowCount) { columns + it }
    private val rowOf = IntArray(size) { if (it >= columns) it - columns else -1 }
    private val tableau = Array(rowCount) { DoubleArray(size) }

    init {
        program.columns.forEachIndexed { j, column ->
            column.lower?.let { lower[j] = approximate(it) }
            column.upper?.let { upper[j] = approximate(it) }
            value[j] = 0.0.coerceIn(lower[j], upper[j])
        }
        program.rows.forEachIndexed { r, row ->
            row.terms.forEach { (j, a) -> tableau[r][j] = approximate(a) }
            val rhs = approximate(row.rhs)
            upper[columns + r] = rhs
            if (row.relation == LinearRelation.EQUAL) lower[columns + r] = rhs
            value[columns + r] = row.terms.keys.sumOf { tableau[r][it] * value[it] }
        }
    }

    /** The optimal basis of `max objective·x` (basic variables, and the nonbasic ones sitting at their upper bound), or null. */
    fun optimalBasis(objective: Map<Int, Double>): Basis? {
        var pivots = 0
        val limit = PIVOT_FACTOR * (size + 1)
        while (true) {
            if (stopped()) throw SimplexStopped()
            if (pivots++ > limit) return null
            val violated = (0 until rowCount).filter { violation(basis[it]) > TOLERANCE }.minByOrNull { basis[it] }
            if (violated != null) {
                if (!repair(violated)) return null
                continue
            }
            when (improve(objective)) {
                Step.OPTIMAL -> return basis()
                Step.UNBOUNDED -> return null
                Step.MOVED -> {}
            }
        }
    }

    /** One general-simplex pivot repairing basic row [r] (Bland's rule); false when it cannot be repaired. */
    private fun repair(r: Int): Boolean {
        val b = basis[r]
        val raise = value[b] < lower[b]
        val entering =
            (0 until size).firstOrNull { k ->
                rowOf[k] < 0 && abs(tableau[r][k]) > PIVOT_TOLERANCE && (if (raise == tableau[r][k] > 0) canIncrease(k) else canDecrease(k))
            } ?: return false
        pivotAndUpdate(r, entering, if (raise) lower[b] else upper[b])
        return true
    }

    /** One primal step with Dantzig's rule. */
    private fun improve(objective: Map<Int, Double>): Step {
        val reduced = DoubleArray(size)
        objective.forEach { (j, c) ->
            val r = rowOf[j]
            if (r < 0) reduced[j] += c else for (k in 0 until size) reduced[k] += c * tableau[r][k]
        }
        var entering = -1
        for (k in 0 until size) {
            if (rowOf[k] >= 0 || abs(reduced[k]) <= TOLERANCE) continue
            val movable = if (reduced[k] > 0) canIncrease(k) else canDecrease(k)
            if (movable && (entering < 0 || abs(reduced[k]) > abs(reduced[entering]))) entering = k
        }
        if (entering < 0) return Step.OPTIMAL
        val direction = if (reduced[entering] > 0) 1.0 else -1.0
        var amount = if (direction > 0) upper[entering] - value[entering] else value[entering] - lower[entering]
        var leaving = -1
        var target = 0.0
        for (r in 0 until rowCount) {
            val a = tableau[r][entering]
            if (abs(a) <= PIVOT_TOLERANCE) continue
            val b = basis[r]
            val rising = (a > 0) == (direction > 0)
            val bound = if (rising) upper[b] else lower[b]
            if (bound.isInfinite()) continue
            val room = (if (rising) bound - value[b] else value[b] - bound).coerceAtLeast(0.0) / abs(a)
            if (room < amount) {
                amount = room
                leaving = r
                target = bound
            }
        }
        if (amount.isInfinite()) return Step.UNBOUNDED
        if (leaving < 0) move(entering, value[entering] + direction * amount) else pivotAndUpdate(leaving, entering, target)
        return Step.MOVED
    }

    private fun pivotAndUpdate(
        r: Int,
        entering: Int,
        target: Double,
    ) {
        val b = basis[r]
        val theta = (target - value[b]) / tableau[r][entering]
        for (i in 0 until rowCount) if (i != r) value[basis[i]] += tableau[i][entering] * theta
        value[b] = target
        value[entering] += theta
        val row = tableau[r]
        val pivot = row[entering]
        for (k in 0 until size) row[k] = -row[k] / pivot
        row[entering] = 0.0
        row[b] = 1.0 / pivot
        for (i in 0 until rowCount) {
            if (i == r) continue
            val other = tableau[i]
            val factor = other[entering]
            if (factor == 0.0) continue
            for (k in 0 until size) other[k] += factor * row[k]
            other[entering] = 0.0
        }
        basis[r] = entering
        rowOf[entering] = r
        rowOf[b] = -1
    }

    private fun move(
        k: Int,
        target: Double,
    ) {
        val change = target - value[k]
        value[k] = target
        for (i in 0 until rowCount) value[basis[i]] += tableau[i][k] * change
    }

    private fun basis(): Basis {
        val atUpper =
            (0 until size).filter { k ->
                rowOf[k] < 0 && upper[k].isFinite() && abs(value[k] - upper[k]) <= abs(value[k] - lower[k])
            }
        return Basis(basis.toSet(), atUpper.toSet())
    }

    private fun violation(k: Int): Double = maxOf(lower[k] - value[k], value[k] - upper[k], 0.0)

    private fun canIncrease(k: Int) = value[k] < upper[k] - TOLERANCE

    private fun canDecrease(k: Int) = value[k] > lower[k] + TOLERANCE

    private enum class Step { OPTIMAL, UNBOUNDED, MOVED }

    /** A basis guess: the basic variables, and the nonbasic ones at their upper bound (the others sit at their lower). */
    class Basis(
        val basic: Set<Int>,
        val atUpper: Set<Int>,
    )

    private companion object {
        const val TOLERANCE = 1e-9
        const val PIVOT_TOLERANCE = 1e-9
        const val PIVOT_FACTOR = 20
    }
}
