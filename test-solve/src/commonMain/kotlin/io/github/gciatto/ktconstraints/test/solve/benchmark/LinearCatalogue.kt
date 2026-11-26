package io.github.gciatto.ktconstraints.test.solve.benchmark

import io.github.gciatto.ktconstraints.core.Rational
import io.github.gciatto.ktconstraints.core.integer.IntLiteral
import io.github.gciatto.ktconstraints.core.integer.IntToReal
import io.github.gciatto.ktconstraints.core.integer.arithmetic.LinearSum
import io.github.gciatto.ktconstraints.core.integer.relation.IntLessOrEqual
import io.github.gciatto.ktconstraints.core.real.RealExpr
import io.github.gciatto.ktconstraints.core.real.RealLiteral
import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.solve.result.SolveStatus
import org.gciatto.kt.math.BigInteger
import kotlin.random.Random

/**
 * Linear and mixed-integer programs, mostly with real objectives, whose optima are known independently of any solver: planted
 * primal-dual pairs, dynamic programming and enumeration. [builders] grows them with `scale`, like [BenchmarkCatalogue].
 */
object LinearCatalogue {
    private const val SEED = 11
    private const val SEED_STRIDE = 7919
    private const val MAX_ENUMERATED_SETS = 22

    /** Builders of every family at [scale] and [seed]. */
    fun builders(
        scale: Int = 1,
        seed: Int = 0,
    ): List<() -> BenchmarkInstance> =
        listOf(
            { plantedLp(8 * scale, 5 * scale, seed) },
            { rationalKnapsack(6 + 4 * scale, seed) },
            { facilityLocation(2 + scale, 4 + 2 * scale, seed) },
            { boundedKnapsack(4 + 2 * scale, seed) },
            { setCover(6 + scale, 12 + 2 * scale, seed) },
            { multiKnapsack(8 + 2 * scale, seed) },
        )

    /** Families whose instances change with the seed: all of them. */
    val SEEDED: Set<String> = setOf("lp-planted", "milp-knapsack", "facility-location", "bounded-knapsack", "set-cover", "multi-knapsack")

    /**
     * `max c·x` subject to `A·x ≤ b`, `x ≥ 0`, with positive `A`, built around a planted optimal pair: a rational
     * vertex `x*` and duals `y*` satisfying complementary slackness, so the optimum is `c·x* = b·y*`.
     */
    fun plantedLp(
        n: Int,
        m: Int,
        seed: Int = 0,
    ): BenchmarkInstance {
        val random = Random(SEED + 31 * n + m + SEED_STRIDE * seed)
        val a = List(m) { List(n) { random.nextInt(1, 10).toLong() } }
        val x = List(n) { if (random.nextInt(3) == 0) Rational.of(random.nextLong(1, 20), random.nextLong(1, 7)) else Rational.ZERO }
        val y = List(m) { if (random.nextInt(2) == 0) Rational.of(random.nextLong(1, 10), random.nextLong(1, 4)) else Rational.ZERO }
        val activity = a.map { row -> row.indices.fold(Rational.ZERO) { sum, j -> sum + x[j] * Rational.of(row[j]) } }
        val b = activity.mapIndexed { i, it -> if (y[i] > Rational.ZERO) it else it + Rational.of(random.nextLong(1, 6)) }
        val c =
            List(n) { j ->
                val reduced = (0 until m).fold(Rational.ZERO) { sum, i -> sum + y[i] * Rational.of(a[i][j]) }
                if (x[j] > Rational.ZERO) reduced else reduced - Rational.of(random.nextLong(1, 6))
            }
        val optimum = b.indices.fold(Rational.ZERO) { sum, i -> sum + b[i] * y[i] }
        check(optimum == x.indices.fold(Rational.ZERO) { sum, j -> sum + c[j] * x[j] }) { "Planted pair is not optimal" }
        val model =
            ConstraintModel.of {
                val xs = List(n) { realVar("x$it") }
                xs.forEach { add(it ge 0) }
                a.forEachIndexed { i, row -> add(sum(xs.mapIndexed { j, v -> v * row[j] }) le b[i]) }
                maximize(sum(xs.mapIndexed { j, v -> v * c[j] }))
            }
        return BenchmarkInstance("lp-planted", n, model, SolveStatus.OPTIMAL, expectedRealObjective = optimum)
    }

    /** 0/1 knapsack over [n] items with rational profits; the optimum comes from dynamic programming on weights. */
    fun rationalKnapsack(
        n: Int,
        seed: Int = 0,
    ): BenchmarkInstance {
        val random = Random(SEED + 17 * n + SEED_STRIDE * seed)
        val weights = List(n) { random.nextInt(3, 25) }
        val profits = List(n) { Rational.of(random.nextLong(5, 60), random.nextLong(1, 5)) }
        val capacity = weights.sum() / 2
        val best = Array(capacity + 1) { Rational.ZERO }
        for (item in 0 until n) {
            for (room in capacity downTo weights[item]) {
                val taken = best[room - weights[item]] + profits[item]
                if (taken > best[room]) best[room] = taken
            }
        }
        val model =
            ConstraintModel.of {
                val take = List(n) { IntToReal(intVar("take$it", 0..1)) }
                add(sum(take.mapIndexed { i, t -> t * weights[i] }) le capacity)
                maximize(sum(take.mapIndexed { i, t -> t * profits[i] }))
            }
        return BenchmarkInstance("milp-knapsack", n, model, SolveStatus.OPTIMAL, expectedRealObjective = best[capacity])
    }

    /**
     * Uncapacitated facility location: open any of [facilities] at a fixed cost and serve each of [customers] from
     * open ones, with continuous shares. The optimum is enumerated over the sets of open facilities.
     */
    fun facilityLocation(
        facilities: Int,
        customers: Int,
        seed: Int = 0,
    ): BenchmarkInstance {
        val random = Random(SEED + 101 * facilities + customers + SEED_STRIDE * seed)
        val opening = List(facilities) { Rational.of(random.nextLong(10, 40)) }
        val serving = List(facilities) { List(customers) { Rational.of(random.nextLong(1, 30), random.nextLong(1, 3)) } }
        val optimum =
            (1 until (1 shl facilities)).minOf { open ->
                val chosen = (0 until facilities).filter { open and (1 shl it) != 0 }
                chosen.fold(Rational.ZERO) { sum, f -> sum + opening[f] } +
                    (0 until customers).fold(Rational.ZERO) { sum, k -> sum + chosen.minOf { serving[it][k] } }
            }
        val model =
            ConstraintModel.of {
                val open = List(facilities) { IntToReal(intVar("open$it", 0..1)) }
                val share = List(facilities) { f -> List(customers) { k -> realVar("share$f-$k") } }
                for (f in 0 until facilities) {
                    for (k in 0 until customers) {
                        add(share[f][k] ge 0)
                        add(share[f][k] le open[f])
                    }
                }
                for (k in 0 until customers) add(sum(share.map { it[k] }) eq 1)
                val fixed = open.mapIndexed { f, o -> o * opening[f] }
                val variable = share.flatMapIndexed { f, row -> row.mapIndexed { k, s -> s * serving[f][k] } }
                minimize(sum(fixed + variable))
            }
        return BenchmarkInstance("facility-location", facilities * customers, model, SolveStatus.OPTIMAL, expectedRealObjective = optimum)
    }

    /**
     * Bounded knapsack with an integer objective: [n] items, each taken up to 50 times, so integer domains are wide.
     * The optimum comes from dynamic programming over the capacity.
     */
    fun boundedKnapsack(
        n: Int,
        seed: Int = 0,
    ): BenchmarkInstance {
        val random = Random(SEED + 59 * n + SEED_STRIDE * seed)
        val copies = 50
        val weights = List(n) { random.nextInt(3, 40) }
        val values = List(n) { random.nextInt(1, 60) }
        val capacity = weights.sum() * copies / 4
        val best = IntArray(capacity + 1)
        for (item in 0 until n) {
            // Bounded multiplicity: a fresh row per item, taking 0..copies of it.
            val previous = best.copyOf()
            for (room in 0..capacity) {
                for (count in 1..copies) {
                    val used = count * weights[item]
                    if (used > room) break
                    best[room] = maxOf(best[room], previous[room - used] + count * values[item])
                }
            }
        }
        val model =
            ConstraintModel.of {
                val take = List(n) { intVar("take$it", 0..copies) }
                add(IntLessOrEqual(LinearSum(take, weights.map { BigInteger.of(it.toLong()) }), IntLiteral(capacity)))
                maximize(LinearSum(take, values.map { BigInteger.of(it.toLong()) }))
            }
        return BenchmarkInstance("bounded-knapsack", n, model, SolveStatus.OPTIMAL, BigInteger.of(best[capacity].toLong()))
    }

    /**
     * Minimum-cost set cover: choose some of [sets] seeded subsets, at seeded costs, so that each of [elements] is in
     * one. Every element is in at least two sets, so a cover exists. The optimum is enumerated over the 2^sets choices.
     */
    fun setCover(
        sets: Int,
        elements: Int,
        seed: Int = 0,
    ): BenchmarkInstance {
        require(sets <= MAX_ENUMERATED_SETS) { "Enumeration needs at most $MAX_ENUMERATED_SETS sets" }
        val random = Random(SEED + 211 * sets + elements + SEED_STRIDE * seed)
        val members = List(sets) { mutableSetOf<Int>() }
        for (element in 0 until elements) {
            val owners = (0 until sets).shuffled(random).take(2 + random.nextInt(2))
            owners.forEach { members[it] += element }
        }
        val costs = List(sets) { random.nextInt(3, 20) }
        require(elements < Long.SIZE_BITS) { "Coverage masks hold at most ${Long.SIZE_BITS - 1} elements" }
        val all = (1L shl elements) - 1
        val masks = members.map { set -> set.fold(0L) { mask, element -> mask or (1L shl element) } }
        var optimum = Int.MAX_VALUE
        for (choice in 1 until (1 shl sets)) {
            var covered = 0L
            var cost = 0
            for (s in 0 until sets) {
                if (choice and (1 shl s) != 0) {
                    covered = covered or masks[s]
                    cost += costs[s]
                }
            }
            if (covered == all && cost < optimum) optimum = cost
        }
        val model =
            ConstraintModel.of {
                val take = List(sets) { intVar("set$it", 0..1) }
                for (element in 0 until elements) {
                    val owners = (0 until sets).filter { element in members[it] }
                    add(IntLessOrEqual(IntLiteral(1), LinearSum(owners.map { take[it] }, owners.map { BigInteger.ONE })))
                }
                minimize(LinearSum(take, costs.map { BigInteger.of(it.toLong()) }))
            }
        return BenchmarkInstance("set-cover", sets, model, SolveStatus.OPTIMAL, BigInteger.of(optimum.toLong()))
    }

    /** 0/1 knapsack under two capacity constraints; the optimum comes from dynamic programming over both capacities. */
    fun multiKnapsack(
        n: Int,
        seed: Int = 0,
    ): BenchmarkInstance {
        val random = Random(SEED + 307 * n + SEED_STRIDE * seed)
        val weights = List(2) { List(n) { random.nextInt(3, 20) } }
        val values = List(n) { random.nextInt(1, 40) }
        val (first, second) = weights.map { it.sum() / 2 }
        val best = Array(first + 1) { IntArray(second + 1) }
        for (item in 0 until n) {
            for (a in first downTo weights[0][item]) {
                for (b in second downTo weights[1][item]) {
                    best[a][b] = maxOf(best[a][b], best[a - weights[0][item]][b - weights[1][item]] + values[item])
                }
            }
        }
        val model =
            ConstraintModel.of {
                val take = List(n) { intVar("take$it", 0..1) }
                weights.zip(listOf(first, second)).forEach { (row, capacity) ->
                    add(IntLessOrEqual(LinearSum(take, row.map { BigInteger.of(it.toLong()) }), IntLiteral(capacity)))
                }
                maximize(LinearSum(take, values.map { BigInteger.of(it.toLong()) }))
            }
        return BenchmarkInstance("multi-knapsack", n, model, SolveStatus.OPTIMAL, BigInteger.of(best[first][second].toLong()))
    }

    /** A balanced sum, so deep left-nested trees do not grow with the number of terms. */
    private fun sum(terms: List<RealExpr>): RealExpr =
        when (terms.size) {
            0 -> RealLiteral(Rational.ZERO)
            1 -> terms.single()
            else -> sum(terms.subList(0, terms.size / 2)) + sum(terms.subList(terms.size / 2, terms.size))
        }
}
