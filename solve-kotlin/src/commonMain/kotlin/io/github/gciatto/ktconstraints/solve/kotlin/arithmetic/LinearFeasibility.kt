package io.github.gciatto.ktconstraints.solve.kotlin.arithmetic

import io.github.gciatto.ktconstraints.core.Rational

/**
 * Exact Fourier-Motzkin feasibility with constructive witness recovery.
 *
 * Elimination uses the least lower/upper pair count and merges proportional rows. This is a complete baseline,
 * not a large-scale LP algorithm: intermediate systems can grow exponentially. The checkpoint is called throughout
 * elimination so the owning search can enforce cancellation and resource limits without reporting false infeasibility.
 */
internal class LinearFeasibility {
    /** Decides the conjunction over [variableCount] real variables, without retaining state between calls. */
    suspend fun solve(
        variableCount: Int,
        constraints: List<LinearInequality>,
        checkpoint: suspend () -> Unit = {},
    ): LinearResult {
        require(variableCount >= 0) { "The variable count must not be negative" }
        checkpoint()
        for (row in constraints) {
            checkpoint()
            require(row.coefficients.keys.all { it in 0 until variableCount }) { "Linear variable outside the declared universe" }
        }
        var rows = compact(constraints, checkpoint) ?: return LinearResult(null)
        val frames = mutableListOf<EliminationFrame>()
        while (rows.isNotEmpty()) {
            checkpoint()
            val variable = chooseVariable(variableCount, rows, checkpoint)
            val affected = rows.filter { variable in it.coefficients }
            frames += EliminationFrame(variable, affected)
            rows = eliminate(variable, rows, checkpoint) ?: return LinearResult(null)
        }
        val values = MutableList(variableCount) { Rational.ZERO }
        for (frame in frames.asReversed()) {
            checkpoint()
            values[frame.variable] = recover(frame, values, checkpoint)
        }
        return LinearResult(values)
    }

    private suspend fun chooseVariable(
        variableCount: Int,
        rows: List<LinearInequality>,
        checkpoint: suspend () -> Unit,
    ): Int {
        val positive = IntArray(variableCount)
        val negative = IntArray(variableCount)
        for (row in rows) {
            checkpoint()
            for ((variable, coefficient) in row.coefficients) {
                if (coefficient > Rational.ZERO) positive[variable]++ else negative[variable]++
            }
        }
        var selected = -1
        var score = Long.MAX_VALUE
        for (variable in 0 until variableCount) {
            if (positive[variable] == 0 && negative[variable] == 0) continue
            val pairs = positive[variable].toLong() * negative[variable].toLong()
            if (pairs < score) {
                selected = variable
                score = pairs
            }
        }
        check(selected >= 0) { "A nonconstant row must contain a variable" }
        return selected
    }

    private suspend fun eliminate(
        variable: Int,
        rows: List<LinearInequality>,
        checkpoint: suspend () -> Unit,
    ): List<LinearInequality>? {
        val positive = mutableListOf<LinearInequality>()
        val negative = mutableListOf<LinearInequality>()
        val retained = linkedMapOf<Map<Int, Rational>, LinearInequality>()
        for (row in rows) {
            checkpoint()
            val coefficient = row.coefficients[variable]
            when {
                coefficient == null -> if (!retain(row, retained)) return null
                coefficient > Rational.ZERO -> positive += row
                else -> negative += row
            }
        }
        for (upper in positive) {
            for (lower in negative) {
                checkpoint()
                if (!retain(combine(variable, upper, lower), retained)) return null
            }
        }
        return retained.values.toList()
    }

    private fun combine(
        variable: Int,
        upper: LinearInequality,
        lower: LinearInequality,
    ): LinearInequality {
        val upperScale = -lower.coefficients.getValue(variable)
        val lowerScale = upper.coefficients.getValue(variable)
        val coefficients = mutableMapOf<Int, Rational>()
        for ((index, coefficient) in upper.coefficients) {
            if (index != variable) coefficients[index] = coefficient * upperScale
        }
        for ((index, coefficient) in lower.coefficients) {
            if (index != variable) coefficients[index] = (coefficients[index] ?: Rational.ZERO) + coefficient * lowerScale
        }
        return LinearInequality(
            coefficients,
            upper.upperBound * upperScale + lower.upperBound * lowerScale,
            upper.strict || lower.strict,
        )
    }

    private suspend fun compact(
        rows: List<LinearInequality>,
        checkpoint: suspend () -> Unit,
    ): List<LinearInequality>? {
        val retained = linkedMapOf<Map<Int, Rational>, LinearInequality>()
        for (original in rows) {
            checkpoint()
            if (!retain(original, retained)) return null
        }
        return retained.values.toList()
    }

    private fun retain(
        original: LinearInequality,
        retained: MutableMap<Map<Int, Rational>, LinearInequality>,
    ): Boolean {
        if (original.coefficients.isEmpty()) {
            return original.upperBound > Rational.ZERO || original.upperBound == Rational.ZERO && !original.strict
        }
        val row = normalize(original)
        val previous = retained[row.coefficients]
        if (previous == null || row.upperBound < previous.upperBound || row.upperBound == previous.upperBound && row.strict) {
            retained[row.coefficients] = row
        }
        return true
    }

    private fun normalize(row: LinearInequality): LinearInequality {
        val first = row.coefficients.values.first()
        val magnitude = if (first < Rational.ZERO) -first else first
        if (magnitude == Rational.ONE) return row
        return LinearInequality(
            row.coefficients.mapValues { (_, coefficient) -> divide(coefficient, magnitude) },
            divide(row.upperBound, magnitude),
            row.strict,
        )
    }

    private suspend fun recover(
        frame: EliminationFrame,
        values: List<Rational>,
        checkpoint: suspend () -> Unit,
    ): Rational {
        var lower: LinearBound? = null
        var upper: LinearBound? = null
        for (row in frame.rows) {
            checkpoint()
            var remainder = row.upperBound
            for ((variable, coefficient) in row.coefficients) {
                if (variable != frame.variable) remainder -= coefficient * values[variable]
            }
            val coefficient = row.coefficients.getValue(frame.variable)
            val bound = LinearBound(divide(remainder, coefficient), row.strict)
            if (coefficient > Rational.ZERO) {
                upper = tighterUpper(upper, bound)
            } else {
                lower = tighterLower(lower, bound)
            }
        }
        return selectValue(lower, upper)
    }

    private fun tighterUpper(
        previous: LinearBound?,
        bound: LinearBound,
    ): LinearBound =
        if (previous == null || bound.value < previous.value || bound.value == previous.value && bound.strict) bound else previous

    private fun tighterLower(
        previous: LinearBound?,
        bound: LinearBound,
    ): LinearBound =
        if (previous == null || bound.value > previous.value || bound.value == previous.value && bound.strict) bound else previous

    private fun selectValue(
        lower: LinearBound?,
        upper: LinearBound?,
    ): Rational {
        if (lower == null) return upper?.let { if (it.strict) it.value - Rational.ONE else it.value } ?: Rational.ZERO
        if (upper == null) return if (lower.strict) lower.value + Rational.ONE else lower.value
        check(lower.value < upper.value || lower.value == upper.value && !lower.strict && !upper.strict) {
            "Elimination produced incompatible reconstruction bounds"
        }
        return divide(lower.value + upper.value, Rational.of(2))
    }

    private fun divide(
        numerator: Rational,
        denominator: Rational,
    ): Rational = Rational.of(numerator.numerator * denominator.denominator, numerator.denominator * denominator.numerator)
}
