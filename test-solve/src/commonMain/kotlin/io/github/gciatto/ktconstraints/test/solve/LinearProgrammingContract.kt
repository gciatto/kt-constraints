package io.github.gciatto.ktconstraints.test.solve

import io.github.gciatto.ktconstraints.core.Rational
import io.github.gciatto.ktconstraints.core.evaluation.GroundEvaluator
import io.github.gciatto.ktconstraints.core.integer.IntToReal
import io.github.gciatto.ktconstraints.core.real.RealExpr
import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.model.ConstraintScope
import io.github.gciatto.ktconstraints.solve.ConstraintSolver
import io.github.gciatto.ktconstraints.solve.result.SolveResult
import io.github.gciatto.ktconstraints.solve.result.SolveStatus
import io.kotest.core.spec.style.funSpec
import io.kotest.datatest.withTests
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/** One linear or mixed-integer linear program with its exact expected outcome. */
data class LinearCase(
    /** Stable test name. */
    val name: String,
    /** Expected status. */
    val status: SolveStatus,
    /** Expected exact optimum, for [SolveStatus.OPTIMAL]. */
    val optimum: Rational? = null,
    /** Declares the program in a fresh scope. */
    val build: ConstraintScope.() -> Unit,
) {
    override fun toString(): String = name
}

/**
 * Shared LP and MILP contracts for routes that optimize real objectives exactly: every optimum is checked against the
 * known exact value, and every returned assignment is validated independently against the constraints and objective.
 */
fun linearProgrammingContract(create: () -> ConstraintSolver) =
    funSpec {
        withTests(nameFn = { "linear-contract/${it.name}" }, LINEAR_CASES) { case ->
            val model = ConstraintModel.of(case.build)
            val result = create().use { it.solve(model) }
            result.status shouldBe case.status
            if (case.status == SolveStatus.OPTIMAL) {
                result.realObjectiveValue shouldBe case.optimum
                result.objectiveValue shouldBe null
            }
            validate(model, result)
        }
        test("linear-contract/unattained-supremum-is-not-optimal") {
            // sup { x | x < 1 } = 1 is not attained: no answer may claim optimality, and any bound must be exact.
            val model =
                ConstraintModel.of {
                    val x = realVar("x")
                    add(x lt 1)
                    add(x ge 0)
                    maximize(x)
                }
            val result = create().use { it.solve(model) }
            result.status shouldNotBe SolveStatus.OPTIMAL
            result.realBestBound?.let { it shouldBe Rational.ONE }
            validate(model, result)
        }
    }

private fun validate(
    model: ConstraintModel,
    result: SolveResult,
) {
    val assignment = result.assignment ?: return
    model.constraints.forEach { GroundEvaluator.evaluate(it, assignment) shouldBe true }
    val objective = model.objective?.expression as? RealExpr ?: return
    result.realObjectiveValue shouldBe GroundEvaluator.evaluate(objective, assignment)
}

private fun r(
    numerator: Long,
    denominator: Long = 1,
) = Rational.of(numerator, denominator)

/** Small programs whose optima are known exactly, covering the LP statuses and integrality. */
val LINEAR_CASES: List<LinearCase> =
    listOf(
        LinearCase("lp/integral-vertex", SolveStatus.OPTIMAL, r(11)) {
            // max 3x + 2y, x + y <= 4, x + 3y <= 6, x <= 3, x, y >= 0: vertex (3, 1).
            val x = realVar("x")
            val y = realVar("y")
            add((x + y) le 4)
            add((x + y * 3) le 6)
            add(x le 3)
            add(x ge 0)
            add(y ge 0)
            maximize(x * 3 + y * 2)
        },
        LinearCase("lp/fractional-vertex", SolveStatus.OPTIMAL, r(8, 3)) {
            // max x + y, 2x + y <= 4, x + 2y <= 4, x, y >= 0: vertex (4/3, 4/3).
            val x = realVar("x")
            val y = realVar("y")
            add((x * 2 + y) le 4)
            add((x + y * 2) le 4)
            add(x ge 0)
            add(y ge 0)
            maximize(x + y)
        },
        LinearCase("lp/equality-and-free-variable", SolveStatus.OPTIMAL, r(0)) {
            // min x - y, x + y = 1, y <= 1/2, x free: (1/2, 1/2).
            val x = realVar("x")
            val y = realVar("y")
            add((x + y) eq 1)
            add(y le r(1, 2))
            minimize(x - y)
        },
        LinearCase("lp/degenerate-vertex", SolveStatus.OPTIMAL, r(1)) {
            // Three constraints meet at the optimum (1, 0); x + y <= 1 is redundant there.
            val x = realVar("x")
            val y = realVar("y")
            add(x le 1)
            add((x + y) le 1)
            add((x * 2 + y) le 2)
            add(x ge 0)
            add(y ge 0)
            maximize(x)
        },
        LinearCase("lp/infeasible", SolveStatus.INFEASIBLE) {
            val x = realVar("x")
            add(x ge 1)
            add(x le 0)
            minimize(x)
        },
        LinearCase("lp/unbounded", SolveStatus.UNBOUNDED) {
            val x = realVar("x")
            val y = realVar("y")
            add(x ge 0)
            add((x - y) le 1)
            maximize(x + y)
        },
        LinearCase("milp/integrality-cuts-the-relaxation", SolveStatus.OPTIMAL, r(3, 2)) {
            // max y, y <= x + 1/2, x + y <= 7/2, x in 0..5: the relaxation reaches 2 at x = 3/2, integers reach 3/2.
            val x = intVar("x", 0..5)
            val y = realVar("y")
            add(y le (IntToReal(x) + r(1, 2)))
            add((IntToReal(x) + y) le r(7, 2))
            maximize(y)
        },
        LinearCase("milp/binary-knapsack-with-a-continuous-slack", SolveStatus.OPTIMAL, r(19, 2)) {
            // max 5a + 4b + 3c + s/2, 2a + 3b + 2c + s <= 6, s <= 2, a, b, c in 0..1, s >= 0: a = b = 1, s = 1.
            val a = intVar("a", 0..1)
            val b = intVar("b", 0..1)
            val c = intVar("c", 0..1)
            val s = realVar("s")
            add((IntToReal(a) * 2 + IntToReal(b) * 3 + IntToReal(c) * 2 + s) le 6)
            add(s le 2)
            add(s ge 0)
            maximize(IntToReal(a) * 5 + IntToReal(b) * 4 + IntToReal(c) * 3 + s * r(1, 2))
        },
        LinearCase("milp/integer-infeasible", SolveStatus.INFEASIBLE) {
            // 2x = 1 has a real solution but no integer one.
            val x = intVar("x", -3..3)
            val y = realVar("y")
            add((IntToReal(x) * 2) eq 1)
            add(y ge 0)
            minimize(y)
        },
    )
