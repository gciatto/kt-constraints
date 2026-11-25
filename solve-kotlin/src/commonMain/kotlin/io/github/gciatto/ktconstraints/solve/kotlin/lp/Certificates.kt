package io.github.gciatto.ktconstraints.solve.kotlin.lp

import io.github.gciatto.ktconstraints.core.Rational
import io.github.gciatto.ktconstraints.solve.kotlin.arithmetic.LinearInequality
import io.github.gciatto.ktconstraints.solve.linear.LinearProgram
import io.github.gciatto.ktconstraints.solve.linear.LinearRelation

/**
 * Exact checks of the evidence a [Simplex] leaves behind, recomputed from the original constraints rather than from
 * the tableau: a Farkas refutation, a dual bound on an objective, or an unbounded ray. A simplex bug can then make a
 * check fail, but never turn into a wrong infeasibility, optimality or unboundedness claim.
 */
internal object Certificates {
    /** The nonnegative combination `Σ multiplier·fact`; strict when any strict fact takes part. */
    fun combine(facts: List<Pair<LinearInequality, Rational>>): LinearInequality {
        val coefficients = HashMap<Int, Rational>()
        var bound = Rational.ZERO
        var strict = false
        for ((fact, multiplier) in facts) {
            require(multiplier > Rational.ZERO) { "Certificate multipliers must be positive" }
            fact.coefficients.forEach { (j, a) -> coefficients[j] = (coefficients[j] ?: Rational.ZERO) + a * multiplier }
            bound += fact.upperBound * multiplier
            strict = strict || fact.strict
        }
        return LinearInequality(coefficients, bound, strict)
    }

    /** Whether the combination of [facts] is `0 ≤ b` with `b < 0`, or `0 < 0`: no point satisfies them all. */
    fun refutes(facts: List<Pair<LinearInequality, Rational>>): Boolean =
        combine(facts).let {
            it.coefficients.isEmpty() && (it.upperBound < Rational.ZERO || it.upperBound == Rational.ZERO && it.strict)
        }

    /** Whether the combination of [facts] is exactly `objective·x ≤ value` (or `<`), bounding the objective by [value]. */
    fun bounds(
        facts: List<Pair<LinearInequality, Rational>>,
        objective: Map<Int, Rational>,
        value: Rational,
    ): Boolean =
        combine(facts).let {
            it.coefficients == objective.filterValues { c -> c != Rational.ZERO } && it.upperBound == value
        }

    /** Whether [ray] is a recession direction of [program]'s relaxation along which `objective·x` grows. */
    fun isRay(
        program: LinearProgram,
        ray: Map<Int, Rational>,
        objective: Map<Int, Rational>,
    ): Boolean {
        fun along(terms: Map<Int, Rational>) = terms.entries.fold(Rational.ZERO) { total, (j, a) -> total + a * (ray[j] ?: Rational.ZERO) }
        val columns =
            program.columns.withIndex().all { (j, column) ->
                val move = ray[j] ?: Rational.ZERO
                (column.lower == null || move >= Rational.ZERO) && (column.upper == null || move <= Rational.ZERO)
            }
        val rows =
            program.rows.all {
                val move = along(it.terms)
                if (it.relation == LinearRelation.EQUAL) move == Rational.ZERO else move <= Rational.ZERO
            }
        return columns && rows && along(objective) > Rational.ZERO
    }

    /** The [program] constraint behind [use], as `Σ a·x ≤ b`: a column bound, or one side of a row through its slack. */
    fun fact(
        program: LinearProgram,
        use: Simplex.Use,
    ): Pair<LinearInequality, Rational> {
        val columns = program.columns.size
        val (terms, bound, strict) =
            if (use.index < columns) {
                val column = program.columns[use.index]
                Triple(mapOf(use.index to Rational.ONE), requireNotNull(if (use.upper) column.upper else column.lower), false)
            } else {
                val row = program.rows[use.index - columns]
                check(use.upper || row.relation == LinearRelation.EQUAL) { "A one-sided row has no lower bound" }
                Triple(row.terms, row.rhs, row.relation == LinearRelation.LESS)
            }
        val sign = if (use.upper) Rational.ONE else Rational.of(-1)
        return LinearInequality(terms.mapValues { it.value * sign }, bound * sign, strict) to use.multiplier
    }
}
