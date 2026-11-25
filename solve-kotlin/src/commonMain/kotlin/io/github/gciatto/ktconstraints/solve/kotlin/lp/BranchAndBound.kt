package io.github.gciatto.ktconstraints.solve.kotlin.lp

import io.github.gciatto.ktconstraints.core.Rational
import io.github.gciatto.ktconstraints.solve.linear.LinearProgram
import org.gciatto.kt.math.BigInteger

/**
 * Exact branch and bound over a [LinearProgram]'s integer columns. Every node tightens integer bounds on one shared
 * [Simplex], which repairs feasibility from the previous basis instead of starting over; nodes are explored depth
 * first, the child nearer the relaxation's value first, and pruned when their relaxation cannot beat the incumbent.
 * Integer columns come from finite domains, so the tree is finite. All arithmetic is exact, so an [Outcome.Optimal]
 * reached without stopping is a proof.
 */
internal class BranchAndBound(
    private val program: LinearProgram,
    private val stopped: () -> Boolean = { false },
) {
    private val simplex = Simplex(program, stopped)
    private val integers = program.columns.indices.filter { program.columns[it].integer }

    /** The best integral solution so far, readable after [SimplexStopped] interrupts [maximize]. */
    var incumbent: Incumbent? = null
        private set

    /** Maximizes `Σ score[j]·x[j]` over the integral points of the program. */
    fun maximize(score: Map<Int, Rational>): Outcome = search(score, emptyList(), stopAtFirst = false)

    private fun search(
        score: Map<Int, Rational>,
        root: List<Tightening>,
        stopAtFirst: Boolean,
    ): Outcome {
        val open = ArrayDeque(listOf(root))
        var best: Incumbent? = null
        while (open.isNotEmpty()) {
            if (stopped()) throw SimplexStopped()
            val node = open.removeLast()
            apply(node)
            if (!simplex.check()) continue
            if (!simplex.maximize(score)) {
                // Meyer: a feasible rational mixed-integer program with an unbounded relaxation is unbounded.
                if (stopAtFirst || search(emptyMap(), node, stopAtFirst = true) != Outcome.Infeasible) return Outcome.Unbounded
                continue
            }
            val bound = simplex.objectiveValue(score)
            if (best != null && bound <= best.score) continue
            val branching = mostFractional()
            if (branching == null) {
                best = Incumbent(values(), bound).also { if (!stopAtFirst) incumbent = it }
                if (stopAtFirst) return Outcome.Optimal(best)
                continue
            }
            val value = simplex.valueOf(branching)
            val floor = floor(value)
            val down = node + Tightening(branching, null, DeltaRational.of(Rational.of(floor)))
            val up = node + Tightening(branching, DeltaRational.of(Rational.of(floor + BigInteger.ONE)), null)
            // Last pushed is explored first: dive towards the nearer integer.
            if (value.real - Rational.of(floor) >= HALF) open.addAll(listOf(down, up)) else open.addAll(listOf(up, down))
        }
        return best?.let { Outcome.Optimal(it) } ?: Outcome.Infeasible
    }

    /** Resets every integer column to its program bounds, then applies [node]'s tightenings. */
    private fun apply(node: List<Tightening>) {
        val lower = integers.associateWith { program.columns[it].lower?.let { b -> DeltaRational.of(b) } }.toMutableMap()
        val upper = integers.associateWith { program.columns[it].upper?.let { b -> DeltaRational.of(b) } }.toMutableMap()
        for (tightening in node) {
            tightening.lower?.let { new -> lower[tightening.index] = lower[tightening.index]?.let { maxOf(it, new) } ?: new }
            tightening.upper?.let { new -> upper[tightening.index] = upper[tightening.index]?.let { minOf(it, new) } ?: new }
        }
        integers.forEach { simplex.bound(it, lower[it], upper[it]) }
    }

    /** The integer column whose value is farthest from an integer, lowest index first; null when all are integral. */
    private fun mostFractional(): Int? {
        var chosen: Int? = null
        var distance = Rational.ZERO
        for (index in integers) {
            val value = simplex.valueOf(index)
            if (value.delta == Rational.ZERO && value.real.isInteger) continue
            val fraction = value.real - Rational.of(floor(value))
            val gap = if (fraction > HALF) Rational.ONE - fraction else fraction
            if (chosen == null || gap > distance) {
                chosen = index
                distance = gap
            }
        }
        return chosen
    }

    private fun values(): List<Rational> {
        val epsilon = simplex.epsilon()
        return List(program.columns.size) { simplex.valueOf(it).at(epsilon) }
    }

    /** The greatest integer below or at [value]; `3 − δ` floors to 2. */
    private fun floor(value: DeltaRational): BigInteger {
        val floor = value.real.floor()
        return if (value.real.isInteger && value.delta < Rational.ZERO) floor - BigInteger.ONE else floor
    }

    /** Bounds a node adds to integer column [index]; null keeps the inherited bound. */
    private class Tightening(
        val index: Int,
        val lower: DeltaRational?,
        val upper: DeltaRational?,
    )

    /** An integral solution: exact values per column, and the score of the relaxation it came from. */
    class Incumbent(
        val values: List<Rational>,
        val score: DeltaRational,
    )

    /** How a search ended. */
    sealed interface Outcome {
        /** No integral point satisfies the program. */
        data object Infeasible : Outcome

        /** Integral points exist and the objective improves without limit. */
        data object Unbounded : Outcome

        /** The best integral point, proven optimal by a complete search. */
        data class Optimal(
            val incumbent: Incumbent,
        ) : Outcome
    }

    private companion object {
        val HALF: Rational = Rational.of(1, 2)
    }
}
