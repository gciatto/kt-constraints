package io.github.gciatto.ktconstraints.solve.kotlin.sat

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlin.math.abs
import kotlin.random.Random

/** Native linear constraints `sum(a * literal) <= bound`, checked against exhaustive truth tables. */
class SatLinearSpec :
    FunSpec({
        test("sat-linear/model-counts-match-truth-tables-with-mixed-clauses") {
            repeat(60) { seed ->
                val random = Random(seed)
                val solver = SatSolver()
                val variables = IntArray(6) { solver.newVariable() }
                val linears =
                    List(1 + random.nextInt(3)) {
                        val literals = variables.filter { random.nextBoolean() }.map { if (random.nextBoolean()) it else -it }
                        val coefficients = LongArray(literals.size) { 1L + random.nextInt(7) }
                        Triple(literals.toIntArray(), coefficients, random.nextLong(0, coefficients.sum() + 1))
                    }
                val clauses = List(random.nextInt(4)) { IntArray(2) { variables.random(random) * if (random.nextBoolean()) 1 else -1 } }
                linears.forEach { (literals, coefficients, bound) -> solver.addLinear(literals, coefficients, bound) }
                clauses.forEach(solver::addClause)

                fun holds(values: BooleanArray): Boolean =
                    linears.all { (literals, coefficients, bound) ->
                        literals.indices.sumOf { if (values[abs(literals[it])] == (literals[it] > 0)) coefficients[it] else 0L } <= bound
                    } &&
                        clauses.all { clause -> clause.any { values[abs(it)] == (it > 0) } }

                val expected =
                    (0 until (1 shl variables.size)).count { mask ->
                        holds(BooleanArray(variables.size + 1) { it > 0 && (mask shr (it - 1)) and 1 == 1 })
                    }
                var found = 0
                while (true) {
                    val values = solver.solve().values ?: break
                    holds(values) shouldBe true
                    found++
                    solver.addClause(IntArray(variables.size) { if (values[variables[it]]) -variables[it] else variables[it] })
                }
                found shouldBe expected
            }
        }

        test("sat-linear/assumptions-are-temporary-and-a-coefficient-above-the-bound-fixes-a-literal") {
            val solver = SatSolver()
            val (a, b, c) = IntArray(3) { solver.newVariable() }
            solver.addLinear(intArrayOf(a, b, c), longArrayOf(2, 2, 5), 4)
            solver.solve(intArrayOf(a, b)).status shouldBe SatStatus.SATISFIABLE
            requireNotNull(solver.solve(intArrayOf(a, b)).values)[c] shouldBe false
            solver.solve(intArrayOf(c)).status shouldBe SatStatus.UNSATISFIABLE
            solver.addClause(intArrayOf(a))
            solver.solve(intArrayOf(b, -c)).status shouldBe SatStatus.SATISFIABLE
        }

        test("sat-linear/rejects-non-positive-coefficients-and-unknown-literals") {
            val solver = SatSolver()
            val a = solver.newVariable()
            shouldThrow<IllegalArgumentException> { solver.addLinear(intArrayOf(a), longArrayOf(0), 1) }
            shouldThrow<IllegalArgumentException> { solver.addLinear(intArrayOf(a + 1), longArrayOf(1), 1) }
            shouldThrow<IllegalArgumentException> { solver.addLinear(intArrayOf(a), longArrayOf(1, 2), 1) }
        }
    })
