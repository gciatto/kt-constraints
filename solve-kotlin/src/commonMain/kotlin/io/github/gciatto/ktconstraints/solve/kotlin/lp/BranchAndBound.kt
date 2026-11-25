package io.github.gciatto.ktconstraints.solve.kotlin.lp

import io.github.gciatto.ktconstraints.core.Rational
import io.github.gciatto.ktconstraints.solve.linear.LinearProgram
import org.gciatto.kt.math.BigInteger

/**
 * Exact branch and bound over a [LinearProgram]'s integer columns. Every node tightens integer bounds on one shared
 * [Simplex], which repairs feasibility from the previous basis instead of starting over; nodes are explored depth
 * first, the child nearer the relaxation's value first, and pruned when their relaxation cannot beat the incumbent.
 * Integer columns come from finite domains, so the tree is finite. All arithmetic is exact, so an [Outcome.Optimal]
 * reached without stopping is a proof; the simplex's own evidence is checked by [Certificates] where it stands alone.
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
            if (!simplex.check()) {
                if (node.isEmpty()) certify(Certificates.refutes(simplex.conflict.map { Certificates.fact(program, it) }), "Farkas")
                continue
            }
            if (!simplex.maximize(score)) {
                certify(Certificates.isRay(program, simplex.ray, score), "ray")
                // Meyer: a feasible rational mixed-integer program with an unbounded relaxation is unbounded.
                if (stopAtFirst || search(emptyMap(), node, stopAtFirst = true) != Outcome.Infeasible) return Outcome.Unbounded
                continue
            }
            val bound = simplex.objectiveValue(score)
            if (node.isEmpty() && integers.isEmpty()) {
                certify(Certificates.bounds(simplex.duals.map { Certificates.fact(program, it) }, score, bound.real), "dual")
            }
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

    /**
     * Root evidence is checked against [program] itself: a refutation, a ray (valid at any node, since nodes only add
     * bounds) and, for pure linear programs, the dual bound. shortcut: deeper nodes' proofs are not checked, since
     * that would need a tree certificate; upgrade when branch and bound gains cuts.
     */
    private fun certify(
        valid: Boolean,
        kind: String,
    ) = check(valid) { "The simplex produced an invalid $kind certificate" }

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
