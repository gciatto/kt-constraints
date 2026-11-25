package io.github.gciatto.ktconstraints.solve.kotlin.lp

import io.github.gciatto.ktconstraints.core.Rational
import io.github.gciatto.ktconstraints.solve.linear.LinearProgram
import io.github.gciatto.ktconstraints.solve.linear.LinearRelation
import io.github.gciatto.ktconstraints.solve.linear.LinearRow

/**
 * Gomory mixed-integer cuts from an optimal [Simplex] vertex, in exact arithmetic. A basic integer column with a
 * fractional value gives the row `x = v + Σ a·s` over nonnegative distances `s` of the nonbasic variables from the
 * bounds they sit at; every integral point satisfies the derived `Σ g·s ≥ 1`, while the vertex itself (all `s = 0`)
 * violates it. The bounds used must be the program's own, so cuts are only taken at the root, where they are valid
 * everywhere. Slack distances are treated as continuous, which keeps cuts valid but can make them weaker.
 */
internal object GomoryCuts {
    /** Up to [limit] cuts, from the most fractional basic integer columns first. */
    fun of(
        simplex: Simplex,
        program: LinearProgram,
        limit: Int,
    ): List<LinearRow> =
        simplex
            .basics()
            .asSequence()
            .filter { it < program.columns.size && program.columns[it].integer }
            .mapNotNull { basic -> fraction(simplex.valueOf(basic))?.let { basic to it } }
            .sortedByDescending { (_, f) -> minOf(f, Rational.ONE - f) }
            .mapNotNull { (basic, f) -> cut(simplex, program, requireNotNull(simplex.rowOf(basic)), f) }
            .take(limit)
            .toList()

    private fun cut(
        simplex: Simplex,
        program: LinearProgram,
        row: Map<Int, Rational>,
        f0: Rational,
    ): LinearRow? {
        // Σ g·s ≥ 1, with s = form − lower or s = upper − form, collected as `terms·x ≥ rhs`.
        val terms = HashMap<Int, Rational>()
        var rhs = Rational.ONE
        for ((k, a) in row) {
            val value = simplex.valueOf(k)
            val atLower = simplex.lowerOf(k) == value
            if (value.delta != Rational.ZERO || (!atLower && simplex.upperOf(k) != value)) return null
            // In `x + Σ a'·s = v`, the moved coefficient is a' = −a at a lower bound and a at an upper bound.
            val moved = if (atLower) -a else a
            val integral = k < program.columns.size && program.columns[k].integer && value.real.isInteger
            val g =
                if (integral) {
                    val fk = moved - Rational.of(moved.floor())
                    if (fk <= f0) fk / f0 else (Rational.ONE - fk) / (Rational.ONE - f0)
                } else {
                    if (moved >= Rational.ZERO) moved / f0 else -moved / (Rational.ONE - f0)
                }
            if (g == Rational.ZERO) continue
            val sign = if (atLower) Rational.ONE else Rational.of(-1)
            simplex.formOf(k).forEach { (j, c) -> terms[j] = (terms[j] ?: Rational.ZERO) + g * sign * c }
            rhs += g * sign * value.real
        }
        val nonZero = terms.filterValues { it != Rational.ZERO }
        if (nonZero.isEmpty()) return null
        return LinearRow(nonZero.mapValues { -it.value }, LinearRelation.LESS_OR_EQUAL, -rhs)
    }

    private fun fraction(value: DeltaRational): Rational? {
        if (value.delta != Rational.ZERO || value.real.isInteger) return null
        return value.real - Rational.of(value.real.floor())
    }
}
