package io.github.gciatto.ktconstraints.solve.kotlin.lp

import io.github.gciatto.ktconstraints.core.Rational
import io.github.gciatto.ktconstraints.solve.linear.LinearProgram
import io.github.gciatto.ktconstraints.solve.linear.LinearRow
import io.github.gciatto.ktconstraints.solve.linear.LinearSolution
import io.github.gciatto.ktconstraints.solve.linear.approximate
import org.gciatto.kt.math.BigInteger

/**
 * Exact branch and cut over a [LinearProgram]'s integer columns. Rounds of [GomoryCuts] first tighten the root
 * relaxation, and the cut pool stays in every node's relaxation. Every node tightens integer bounds on one shared
 * [Simplex], which repairs feasibility from the previous basis instead of starting over. The search plunges: after a
 * branching it dives into the child nearer the relaxation's value, and once a dive ends it resumes from the open node
 * with the best bound, so [bestBound] tightens steadily. Columns are chosen by pseudocost, the objective lost per unit
 * of past branchings, and every fractional relaxation is also rounded, which often finds incumbents early. Nodes are
 * pruned when their relaxation cannot beat the incumbent. Integer columns come from finite domains, so the tree is
 * finite. All arithmetic is exact, so an [Outcome.Optimal] reached without stopping is a proof; the simplex's own
 * evidence is checked by [Certificates] where it stands alone.
 */
internal class BranchAndBound(
    private val program: LinearProgram,
    private val stopped: () -> Boolean = { false },
) {
    private val simplex = Simplex(program, stopped = stopped)

    /** Gomory cuts added at the root; valid for every integral point, so every node's relaxation shares them. */
    private val cuts = mutableListOf<LinearRow>()
    private val integers = program.columns.indices.filter { program.columns[it].integer }
    private val pseudocosts = HashMap<Int, Pseudocost>()

    /** Unexplored nodes of the main search, and the node being explored: together they bound what is left. */
    private var frontier: List<Node> = emptyList()

    /** The latest root relaxation value: before any node is branched, it bounds every integral solution. */
    private var rootBound: DeltaRational? = null
    private var current: Node? = null

    /** The best integral solution so far, readable after [SimplexStopped] interrupts [maximize]. */
    var incumbent: Incumbent? = null
        private set

    /**
     * After [SimplexStopped] interrupts [maximize]: an upper bound on the score of every integral solution, or null
     * when the root relaxation was not solved. Every unexplored node's bound is its parent's relaxation value.
     */
    fun bestBound(): DeltaRational? {
        val pending = frontier + listOfNotNull(current)
        val open = if (pending.isEmpty() || pending.any { it.bound == null }) rootBound else pending.mapNotNull { it.bound }.max()
        return open?.let { listOfNotNull(it, incumbent?.score).max() }
    }

    private var nodes = 0L

    /** Search counters, for result metadata. */
    fun statistics(): Map<String, String> =
        mapOf("nodes" to nodes.toString(), "pivots" to simplex.pivots.toString(), "cuts" to cuts.size.toString())

    /** Maximizes `Σ score[j]·x[j]` over the integral points of the program. */
    fun maximize(score: Map<Int, Rational>): Outcome {
        val guess = FloatingSimplex(program, stopped).optimalBasis(score.mapValues { approximate(it.value) })
        if (guess != null) {
            VerifiedStart.optimum(program, score, guess)?.let { proven ->
                incumbent = proven
                return Outcome.Optimal(proven)
            }
            simplex.warmStart(guess)
        }
        if (integers.isNotEmpty()) cut(score)
        return search(score, emptyList(), stopAtFirst = false)
    }

    /**
     * Tightens the root relaxation with rounds of Gomory cuts while they keep lowering its value; the search then
     * starts over on the tighter relaxation. shortcut: cuts are only generated at the root and never removed from the
     * pool; upgrade to local cuts and pool aging when benchmarks show large trees or bloated tableaux.
     */
    private fun cut(score: Map<Int, Rational>) {
        var previous: DeltaRational? = null
        repeat(CUT_ROUNDS) {
            apply(emptyList())
            if (!simplex.check() || !simplex.maximize(score)) return
            val value = simplex.objectiveValue(score)
            rootBound = value
            if (previous?.let { value >= it } == true) return
            previous = value
            val round = GomoryCuts.of(simplex, program, CUTS_PER_ROUND)
            if (round.isEmpty()) return
            cuts += round
            simplex.addRows(round)
        }
    }

    @Suppress("CyclomaticComplexMethod", "LongMethod") // One loop owns node selection, pruning and branching.
    private fun search(
        score: Map<Int, Rational>,
        root: List<Tightening>,
        stopAtFirst: Boolean,
    ): Outcome {
        val open = mutableListOf(Node(root, null, null))
        if (!stopAtFirst) frontier = open
        var plunge = true
        var best: Incumbent? = null

        fun improve(found: Incumbent) {
            best = found
            if (!stopAtFirst) incumbent = found
        }
        while (open.isNotEmpty()) {
            if (stopped()) throw SimplexStopped()
            // shortcut: best-bound selection scans the open list; upgrade to a heap if trees reach many thousands of nodes.
            val node = if (plunge) open.removeLast() else open.removeAt(open.indices.maxBy { requireNotNull(open[it].bound) })
            if (!stopAtFirst) current = node
            plunge = false
            val incumbentScore = best?.score
            if (incumbentScore != null && node.bound != null && node.bound <= incumbentScore) continue
            nodes++
            apply(node.tightenings)
            if (!simplex.check()) {
                // With cuts, a root refutation may use them; they are valid, but not part of the program to check against.
                if (node.tightenings.isEmpty() && cuts.isEmpty()) {
                    certify(
                        Certificates.refutes(simplex.conflict.map { Certificates.fact(program, it) }),
                        "Farkas",
                    )
                }
                continue
            }
            if (!simplex.maximize(score)) {
                certify(Certificates.isRay(program, simplex.ray, score), "ray")
                // Meyer: a feasible rational mixed-integer program with an unbounded relaxation is unbounded.
                if (stopAtFirst || search(emptyMap(), node.tightenings, stopAtFirst = true) != Outcome.Infeasible) return Outcome.Unbounded
                continue
            }
            val bound = simplex.objectiveValue(score)
            if (!stopAtFirst && node.tightenings.isEmpty()) rootBound = bound
            if (node.tightenings.isEmpty() && integers.isEmpty()) {
                certify(Certificates.bounds(simplex.duals.map { Certificates.fact(program, it) }, score, bound.real), "dual")
            }
            if (!stopAtFirst) node.branch?.let { learn(it, requireNotNull(node.bound), bound) }
            if (incumbentScore != null && bound <= incumbentScore) continue
            val branching = branching()
            if (branching == null) {
                val found = Incumbent(values(), bound)
                improve(found)
                if (stopAtFirst) return Outcome.Optimal(found)
                continue
            }
            rounded(score)?.let { found ->
                if (best?.let { found.score > it.score } != false) improve(found)
                if (stopAtFirst) return Outcome.Optimal(found)
            }
            val value = simplex.valueOf(branching)
            val floor = floor(value)
            val fraction = value.real - Rational.of(floor)
            val down =
                Node(
                    node.tightenings + Tightening(branching, null, DeltaRational.of(Rational.of(floor))),
                    bound,
                    Branch(branching, false, fraction),
                )
            val up =
                Node(
                    node.tightenings + Tightening(branching, DeltaRational.of(Rational.of(floor + BigInteger.ONE)), null),
                    bound,
                    Branch(branching, true, Rational.ONE - fraction),
                )
            // Last pushed is explored next: dive towards the nearer integer.
            if (fraction >= HALF) open.addAll(listOf(down, up)) else open.addAll(listOf(up, down))
            plunge = true
        }
        if (!stopAtFirst) current = null
        return best?.let { Outcome.Optimal(it) } ?: Outcome.Infeasible
    }

    /** Records how much a branching on [branch]'s column cost, per unit it moved the column. */
    private fun learn(
        branch: Branch,
        parent: DeltaRational,
        bound: DeltaRational,
    ) {
        if (branch.distance == Rational.ZERO) return
        val loss = (parent.real - bound.real).let { if (it < Rational.ZERO) Rational.ZERO else it }
        pseudocosts.getOrPut(branch.index) { Pseudocost() }.record(branch.up, loss / branch.distance)
    }

    /**
     * The fractional integer column whose branching is expected to lose the most on both sides (the product of the
     * pseudocost estimates, unknown ones taken as 1), lowest index first; null when all are integral.
     */
    private fun branching(): Int? {
        var chosen: Int? = null
        var best = Rational.ZERO
        for (index in integers) {
            val value = simplex.valueOf(index)
            if (value.delta == Rational.ZERO && value.real.isInteger) continue
            val fraction = value.real - Rational.of(floor(value))
            val costs = pseudocosts[index]
            val down = maxOf((costs?.estimate(false) ?: Rational.ONE) * fraction, MINIMUM_GAIN)
            val up = maxOf((costs?.estimate(true) ?: Rational.ONE) * (Rational.ONE - fraction), MINIMUM_GAIN)
            val gain = down * up
            if (chosen == null || gain > best) {
                chosen = index
                best = gain
            }
        }
        return chosen
    }

    /** The relaxation with every integer column rounded to its nearest integer, when that is a solution. */
    private fun rounded(score: Map<Int, Rational>): Incumbent? {
        val values = values().toMutableList()
        integers.forEach { values[it] = (values[it] + HALF).floor().let { floor -> Rational.of(floor) } }
        if (!LinearSolution(program, values).isFeasible) return null
        return Incumbent(values, DeltaRational.of(score.entries.fold(Rational.ZERO) { sum, (j, c) -> sum + c * values[j] }))
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

    private fun values(): List<Rational> {
        val epsilon = simplex.epsilon()
        return List(program.columns.size) { simplex.valueOf(it).at(epsilon) }
    }

    /** The greatest integer below or at [value]; `3 − δ` floors to 2. */
    private fun floor(value: DeltaRational): BigInteger {
        val floor = value.real.floor()
        return if (value.real.isInteger && value.delta < Rational.ZERO) floor - BigInteger.ONE else floor
    }

    /** Pending work: bounds on top of the program's, the parent's relaxation value, and the branching that made it. */
    private class Node(
        val tightenings: List<Tightening>,
        val bound: DeltaRational?,
        val branch: Branch?,
    )

    /** A branching on column [index], [up] or down, moving it by [distance] from the parent's value. */
    private class Branch(
        val index: Int,
        val up: Boolean,
        val distance: Rational,
    )

    /** Average objective loss per unit of branching on one column, per direction. */
    private class Pseudocost {
        private val total = arrayOf(Rational.ZERO, Rational.ZERO)
        private val count = intArrayOf(0, 0)

        fun record(
            up: Boolean,
            loss: Rational,
        ) {
            val side = if (up) 1 else 0
            total[side] += loss
            count[side]++
        }

        fun estimate(up: Boolean): Rational? {
            val side = if (up) 1 else 0
            return if (count[side] == 0) null else total[side] / Rational.of(count[side].toLong())
        }
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
        val MINIMUM_GAIN: Rational = Rational.of(1, 1_000_000)
        const val CUT_ROUNDS = 5
        const val CUTS_PER_ROUND = 8
    }
}
