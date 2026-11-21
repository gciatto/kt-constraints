package io.github.gciatto.ktconstraints.solve.kotlin.sat

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlin.math.abs
import kotlin.random.Random

/** Checks SAT semantics independently by evaluating clauses over exhaustive tiny truth tables. */
class SatSolverSpec :
    FunSpec({
        test("sat/empty-formula-has-an-empty-model") {
            val result = SatSolver().solve()
            result.status shouldBe SatStatus.SATISFIABLE
            requireNotNull(result.values).size shouldBe 1
        }

        test("sat/empty-clause-is-a-permanent-contradiction") {
            val solver = SatSolver()
            solver.addClause(intArrayOf())
            solver.solve().status shouldBe SatStatus.UNSATISFIABLE
            solver.newVariable()
            solver.solve().status shouldBe SatStatus.UNSATISFIABLE
        }

        test("sat/units-propagate-through-implications") {
            val solver = SatSolver()
            val a = solver.newVariable()
            val b = solver.newVariable()
            val c = solver.newVariable()
            solver.addClause(intArrayOf(a))
            solver.addClause(intArrayOf(-a, b))
            solver.addClause(intArrayOf(-b, c))
            val values = requireNotNull(solver.solve().values)
            listOf(values[a], values[b], values[c]) shouldBe listOf(true, true, true)
            solver.addClause(intArrayOf(-c))
            solver.solve().status shouldBe SatStatus.UNSATISFIABLE
        }

        test("sat/duplicates-collapse-and-tautologies-do-not-restrict") {
            val solver = SatSolver()
            val a = solver.newVariable()
            val b = solver.newVariable()
            solver.addClause(intArrayOf(a, -a, b))
            solver.addClause(intArrayOf(-b, -b))
            val values = requireNotNull(solver.solve(intArrayOf(-a)).values)
            values[a] shouldBe false
            values[b] shouldBe false
        }

        test("sat/clauses-copy-the-callers-array") {
            val solver = SatSolver()
            val a = solver.newVariable()
            val clause = intArrayOf(a)
            solver.addClause(clause)
            clause[0] = -a
            requireNotNull(solver.solve().values)[a] shouldBe true
        }

        test("sat/rejects-invalid-literals-without-changing-the-formula") {
            val solver = SatSolver()
            val a = solver.newVariable()
            for (invalid in listOf(0, Int.MIN_VALUE, a + 1, -(a + 1))) {
                shouldThrow<IllegalArgumentException> { solver.addClause(intArrayOf(a, invalid)) }
            }
            requireNotNull(solver.solve(intArrayOf(-a)).values)[a] shouldBe false
        }

        test("sat/exhausts-every-formula-over-two-variables") {
            val candidates =
                listOf(
                    intArrayOf(1), intArrayOf(-1), intArrayOf(2), intArrayOf(-2),
                    intArrayOf(1, 2), intArrayOf(1, -2), intArrayOf(-1, 2), intArrayOf(-1, -2),
                )
            repeat(1 shl candidates.size) { mask ->
                val clauses = candidates.filterIndexed { index, _ -> mask and (1 shl index) != 0 }
                val solver = SatSolver()
                repeat(2) { solver.newVariable() }
                clauses.forEach(solver::addClause)
                checkSatResult(2, clauses, intArrayOf(), solver.solve())
            }
        }

        test("sat/seeded-formulas-match-an-independent-truth-table") {
            val random = Random(0x5A7)
            repeat(400) {
                val count = random.nextInt(1, 8)
                val clauses =
                    List(random.nextInt(1, 28)) {
                        IntArray(random.nextInt(1, 6)) {
                            val variable = random.nextInt(1, count + 1)
                            if (random.nextBoolean()) variable else -variable
                        }
                    }
                val solver = SatSolver()
                repeat(count) { solver.newVariable() }
                clauses.forEach(solver::addClause)
                checkSatResult(count, clauses, intArrayOf(), solver.solve())
            }
        }

        test("sat/assumptions-do-not-become-permanent-clauses") {
            val clauses = listOf(intArrayOf(-1, 2), intArrayOf(-1, -2))
            val solver = SatSolver()
            repeat(2) { solver.newVariable() }
            clauses.forEach(solver::addClause)
            solver.solve(intArrayOf(1)).status shouldBe SatStatus.UNSATISFIABLE
            checkSatResult(2, clauses, intArrayOf(), solver.solve())
            checkSatResult(2, clauses, intArrayOf(-1), solver.solve(intArrayOf(-1)))
            solver.solve(intArrayOf(2, -2)).status shouldBe SatStatus.UNSATISFIABLE
            checkSatResult(2, clauses, intArrayOf(), solver.solve())
        }

        test("sat/learned-clauses-survive-many-different-assumption-checks") {
            val random = Random(173)
            repeat(50) {
                val count = 6
                val clauses =
                    List(24) {
                        IntArray(3) {
                            val variable = random.nextInt(1, count + 1)
                            if (random.nextBoolean()) variable else -variable
                        }
                    }
                val solver = SatSolver()
                repeat(count) { solver.newVariable() }
                clauses.forEach(solver::addClause)
                repeat(12) {
                    val assumptions =
                        IntArray(random.nextInt(0, 4)) {
                            val variable = random.nextInt(1, count + 1)
                            if (random.nextBoolean()) variable else -variable
                        }
                    checkSatResult(count, clauses, assumptions, solver.solve(assumptions))
                }
                checkSatResult(count, clauses, intArrayOf(), solver.solve())
            }
        }

        test("sat/watches-move-through-a-long-clause") {
            val solver = SatSolver()
            val variables = IntArray(80) { solver.newVariable() }
            solver.addClause(variables)
            val assumptions = variables.dropLast(1).map { -it }.toIntArray()
            requireNotNull(solver.solve(assumptions).values)[variables.last()] shouldBe true
            solver.solve(variables.map { -it }.toIntArray()).status shouldBe SatStatus.UNSATISFIABLE
            solver.solve().status shouldBe SatStatus.SATISFIABLE
        }

        test("sat/new-variables-and-blocking-clauses-work-after-a-solve") {
            val solver = SatSolver()
            val a = solver.newVariable()
            val first = requireNotNull(solver.solve().values)
            solver.addClause(intArrayOf(if (first[a]) -a else a))
            val b = solver.newVariable()
            solver.addClause(intArrayOf(b))
            val second = requireNotNull(solver.solve().values)
            second[a] shouldBe !first[a]
            second[b] shouldBe true
            first.size shouldBe 2
        }

        test("sat/pigeonhole-requires-conflict-learning") {
            val solver = SatSolver()
            val positions = List(4) { IntArray(3) { solver.newVariable() } }
            positions.forEach(solver::addClause)
            for (hole in 0 until 3) {
                for (left in positions.indices) {
                    for (right in left + 1 until positions.size) {
                        solver.addClause(intArrayOf(-positions[left][hole], -positions[right][hole]))
                    }
                }
            }
            solver.solve().status shouldBe SatStatus.UNSATISFIABLE
            (solver.conflicts > 0) shouldBe true
        }
    })

private fun checkSatResult(
    variables: Int,
    clauses: List<IntArray>,
    assumptions: IntArray,
    result: SatResult,
) {
    fun accepts(values: BooleanArray): Boolean =
        (clauses + assumptions.map { intArrayOf(it) }).all { clause ->
            clause.any { literal -> values[abs(literal)] == (literal > 0) }
        }
    val exists =
        (0 until (1 shl variables)).any { mask ->
            accepts(BooleanArray(variables + 1) { index -> index > 0 && mask and (1 shl (index - 1)) != 0 })
        }
    result.status shouldBe if (exists) SatStatus.SATISFIABLE else SatStatus.UNSATISFIABLE
    if (exists) {
        val values = requireNotNull(result.values)
        values.size shouldBe variables + 1
        accepts(values) shouldBe true
    } else {
        result.values shouldBe null
    }
}
