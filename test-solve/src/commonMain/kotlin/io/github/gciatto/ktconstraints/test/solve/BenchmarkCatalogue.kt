package io.github.gciatto.ktconstraints.test.solve

import io.github.gciatto.ktconstraints.core.AllDifferent
import io.github.gciatto.ktconstraints.core.BitVecBinary
import io.github.gciatto.ktconstraints.core.BitVecComparison
import io.github.gciatto.ktconstraints.core.BitVecLiteral
import io.github.gciatto.ktconstraints.core.BitVecOperator
import io.github.gciatto.ktconstraints.core.BitVecRelation
import io.github.gciatto.ktconstraints.core.BoolNot
import io.github.gciatto.ktconstraints.core.BoolOr
import io.github.gciatto.ktconstraints.core.Element
import io.github.gciatto.ktconstraints.core.IntDifference
import io.github.gciatto.ktconstraints.core.IntEquality
import io.github.gciatto.ktconstraints.core.IntExpr
import io.github.gciatto.ktconstraints.core.IntLessOrEqual
import io.github.gciatto.ktconstraints.core.IntLiteral
import io.github.gciatto.ktconstraints.core.IntSum
import io.github.gciatto.ktconstraints.core.LinearSum
import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.solve.SolveStatus
import org.gciatto.kt.math.BigInteger
import kotlin.random.Random

/**
 * Benchmark families with answers known independently of any solver: closed forms, dynamic programming or exhaustive
 * enumeration. [scale] grows the sizes; 1 keeps every instance small enough for every route that supports it.
 */
object BenchmarkCatalogue {
    private const val SEED = 7

    /** Every family at [scale]. */
    fun instances(scale: Int = 1): List<BenchmarkInstance> = builders(scale).map { it() }

    /** Builders of every family at [scale], so model construction can be timed. */
    fun builders(scale: Int = 1): List<() -> BenchmarkInstance> =
        listOf(
            { queens(4 + 4 * scale) },
            { coloring(5 + 2 * scale) },
            { pigeonhole(4 + scale) },
            { knapsack(8 + 2 * scale) },
            { assignment(4 + scale) },
            { jobShop() },
            { bitVectors(8 * scale) },
            { integerProgram(3 + scale) },
        )

    /** N-queens; solutions exist for every n of at least 4. */
    fun queens(n: Int): BenchmarkInstance {
        val model =
            ConstraintModel.of {
                val xs = List(n) { intVar("q$it", 0 until n) }
                for (i in 0 until n) {
                    for (j in i + 1 until n) {
                        add(BoolNot(IntEquality(xs[i], xs[j])))
                        add(BoolNot(IntEquality(IntDifference(xs[i], xs[j]), IntLiteral(i - j))))
                        add(BoolNot(IntEquality(IntDifference(xs[i], xs[j]), IntLiteral(j - i))))
                    }
                }
            }
        return BenchmarkInstance("n-queens", n, model, SolveStatus.FEASIBLE)
    }

    /** Minimum colouring of an odd cycle of [n] vertices: three colours, so the largest colour index is 2. */
    fun coloring(n: Int): BenchmarkInstance {
        require(n % 2 == 1) { "Odd cycles need three colours" }
        val model =
            ConstraintModel.of {
                val colors = List(n) { intVar("c$it", 0 until n) }
                val highest = intVar("highest", 0 until n)
                colors.indices.forEach { add(BoolNot(IntEquality(colors[it], colors[(it + 1) % n]))) }
                colors.forEach { add(IntLessOrEqual(it, highest)) }
                minimize(highest)
            }
        return BenchmarkInstance("graph-coloring", n, model, SolveStatus.OPTIMAL, BigInteger.TWO)
    }

    /** [n] + 1 pigeons in [n] holes: infeasible. */
    fun pigeonhole(n: Int): BenchmarkInstance {
        val model = ConstraintModel.of { add(AllDifferent(List(n + 1) { intVar("p$it", 0 until n) })) }
        return BenchmarkInstance("pigeonhole", n, model, SolveStatus.INFEASIBLE)
    }

    /** 0/1 knapsack over [n] seeded items; the optimum comes from dynamic programming. */
    fun knapsack(n: Int): BenchmarkInstance {
        val random = Random(SEED + n)
        val weights = List(n) { random.nextInt(3, 20) }
        val values = List(n) { random.nextInt(1, 30) }
        val capacity = weights.sum() / 2
        val best = IntArray(capacity + 1)
        for (item in 0 until n) {
            for (room in capacity downTo weights[item]) best[room] = maxOf(best[room], best[room - weights[item]] + values[item])
        }
        val model =
            ConstraintModel.of {
                val take = List(n) { intVar("take$it", 0..1) }
                add(IntLessOrEqual(LinearSum(take, weights.map { BigInteger.of(it.toLong()) }), IntLiteral(capacity)))
                maximize(LinearSum(take, values.map { BigInteger.of(it.toLong()) }))
            }
        return BenchmarkInstance("knapsack", n, model, SolveStatus.OPTIMAL, BigInteger.of(best[capacity].toLong()))
    }

    /** Assignment of [n] workers to [n] tasks with seeded costs through `element`; the optimum is enumerated. */
    fun assignment(n: Int): BenchmarkInstance {
        val random = Random(SEED * n)
        val costs = List(n) { List(n) { random.nextInt(1, 50) } }
        val model =
            ConstraintModel.of {
                val task = List(n) { intVar("task$it", 0 until n) }
                add(AllDifferent(task))
                val total = task.indices.map { worker -> Element(task[worker], costs[worker].map { IntLiteral(it) }) }
                minimize(total.reduce<IntExpr, IntExpr> { sum, cost -> IntSum(sum, cost) })
            }
        val optimum = cheapestAssignment(costs)
        return BenchmarkInstance("assignment", n, model, SolveStatus.OPTIMAL, BigInteger.of(optimum.toLong()))
    }

    /** OR-Tools' three-job, three-machine example; its optimal makespan is 11. */
    @Suppress("MagicNumber") // The published instance.
    fun jobShop(): BenchmarkInstance {
        val jobs = listOf(listOf(0 to 3, 1 to 2, 2 to 2), listOf(0 to 2, 2 to 1, 1 to 4), listOf(1 to 4, 2 to 3))
        val horizon = jobs.flatten().sumOf { it.second }
        val model =
            ConstraintModel.of {
                val starts = jobs.mapIndexed { j, tasks -> tasks.indices.map { t -> intVar("s${j}_$t", 0..horizon) } }
                val makespan = intVar("makespan", 0..horizon)
                jobs.forEachIndexed { j, tasks ->
                    tasks.indices.forEach { t ->
                        val end = IntSum(starts[j][t], IntLiteral(tasks[t].second))
                        if (t + 1 < tasks.size) add(IntLessOrEqual(end, starts[j][t + 1])) else add(IntLessOrEqual(end, makespan))
                    }
                }
                val byMachine =
                    jobs.flatMapIndexed {
                        j,
                        tasks,
                        ->
                        tasks.mapIndexed { t, (machine, duration) -> Triple(machine, starts[j][t], duration) }
                    }
                byMachine.groupBy { it.first }.values.forEach { onMachine ->
                    for (a in onMachine.indices) {
                        for (b in a + 1 until onMachine.size) {
                            val (_, sa, da) = onMachine[a]
                            val (_, sb, db) = onMachine[b]
                            add(BoolOr(IntLessOrEqual(IntSum(sa, IntLiteral(da)), sb), IntLessOrEqual(IntSum(sb, IntLiteral(db)), sa)))
                        }
                    }
                }
                minimize(makespan)
            }
        return BenchmarkInstance("job-shop", 3, model, SolveStatus.OPTIMAL, BigInteger.of(11))
    }

    /** A [width]-bit `x * 3 = 5`: solvable for every width because 3 is odd. */
    fun bitVectors(width: Int): BenchmarkInstance {
        val model =
            ConstraintModel.of {
                val x = bitVecVar("x", width)
                val product = BitVecBinary(BitVecOperator.MULTIPLY, x, BitVecLiteral(BigInteger.of(3), width))
                add(BitVecComparison(BitVecRelation.EQUAL, product, BitVecLiteral(BigInteger.of(5), width)))
            }
        return BenchmarkInstance("bit-vectors", width, model, SolveStatus.FEASIBLE)
    }

    /** A seeded integer program over [n] variables in 0..4 with three constraints; the optimum is enumerated. */
    fun integerProgram(n: Int): BenchmarkInstance {
        val random = Random(SEED + 100 * n)
        val rows = List(3) { List(n) { random.nextInt(1, 6) } }
        val bounds = rows.map { it.sum() * 2 }
        val profits = List(n) { random.nextInt(1, 10) }
        val model =
            ConstraintModel.of {
                val xs = List(n) { intVar("x$it", 0..4) }
                rows.zip(bounds).forEach { (row, bound) ->
                    add(IntLessOrEqual(LinearSum(xs, row.map { BigInteger.of(it.toLong()) }), IntLiteral(bound)))
                }
                maximize(LinearSum(xs, profits.map { BigInteger.of(it.toLong()) }))
            }
        val optimum =
            points(n)
                .filter { x -> rows.zip(bounds).all { (row, bound) -> row.indices.sumOf { row[it] * x[it] } <= bound } }
                .maxOf { x -> x.indices.sumOf { profits[it] * x[it] } }
        return BenchmarkInstance("integer-program", n, model, SolveStatus.OPTIMAL, BigInteger.of(optimum.toLong()))
    }

    /** Exact minimum-cost assignment by dynamic programming over task subsets: O(n 2^n), not n! permutations. */
    private fun cheapestAssignment(costs: List<List<Int>>): Int {
        val n = costs.size
        val best = IntArray(1 shl n) { Int.MAX_VALUE }
        best[0] = 0
        for (taken in best.indices) {
            if (best[taken] == Int.MAX_VALUE) continue
            val worker = taken.countOneBits()
            if (worker == n) continue
            for (task in 0 until n) {
                if (taken and (1 shl task) == 0) {
                    val next = taken or (1 shl task)
                    best[next] = minOf(best[next], best[taken] + costs[worker][task])
                }
            }
        }
        return best.last()
    }

    private fun points(n: Int): List<List<Int>> =
        if (n ==
            0
        ) {
            listOf(emptyList())
        } else {
            points(n - 1).flatMap { rest -> (0..4).map { rest + it } }
        }
}
