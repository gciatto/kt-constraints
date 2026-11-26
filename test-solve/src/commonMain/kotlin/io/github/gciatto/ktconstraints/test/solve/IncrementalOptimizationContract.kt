package io.github.gciatto.ktconstraints.test.solve

import io.github.gciatto.ktconstraints.core.Rational
import io.github.gciatto.ktconstraints.core.VariableFactory
import io.github.gciatto.ktconstraints.core.bool.BoolOr
import io.github.gciatto.ktconstraints.core.domain.IntDomain
import io.github.gciatto.ktconstraints.core.integer.IntLiteral
import io.github.gciatto.ktconstraints.core.integer.arithmetic.LinearSum
import io.github.gciatto.ktconstraints.core.integer.relation.IntLessOrEqual
import io.github.gciatto.ktconstraints.model.ConstraintScope
import io.github.gciatto.ktconstraints.model.Objective
import io.github.gciatto.ktconstraints.solve.incremental.CheckStatus
import io.github.gciatto.ktconstraints.solve.incremental.IncrementalConstraintSolver
import io.github.gciatto.ktconstraints.solve.result.SolveStatus
import io.github.gciatto.ktconstraints.solve.result.TerminationReason
import io.kotest.core.spec.style.funSpec
import io.kotest.matchers.shouldBe
import org.gciatto.kt.math.BigInteger

/**
 * Shared contracts of incremental optimization: optima follow the session's edits, checkpoints and assumptions, and
 * neither objectives nor assumptions outlive their call. Routes with exact real objectives pass [realObjectives].
 */
fun incrementalOptimizationContract(
    realObjectives: Boolean = true,
    create: () -> IncrementalConstraintSolver,
) = funSpec {
    fun coefficients(vararg values: Long) = values.map { BigInteger.of(it) }

    test("incremental-optimization/optima-follow-edits-and-checkpoints") {
        ConstraintScope.empty {
            val x = intVar("x", 0..9)
            val y = intVar("y", 0..9)
            add(IntLessOrEqual(LinearSum(listOf(x, y), coefficients(1, 1)), IntLiteral(12)))
            val goal = Objective.Maximize(LinearSum(listOf(x, y), coefficients(2, 1)))
            create().use { solver ->
                solver.openSession(buildModel()).use { session ->
                    val first = session.optimize(goal)
                    first.status shouldBe SolveStatus.OPTIMAL
                    first.objectiveValue shouldBe BigInteger.of(21)
                    first.bestBound shouldBe BigInteger.of(21)
                    first.assignment?.valueOf(x) shouldBe BigInteger.of(9)
                    val before = session.push()
                    session.add(y ge 5)
                    session.optimize(goal).objectiveValue shouldBe BigInteger.of(19)
                    session.popTo(before)
                    session.optimize(goal).objectiveValue shouldBe BigInteger.of(21)
                }
            }
        }
    }

    test("incremental-optimization/objectives-and-assumptions-hold-for-one-call") {
        ConstraintScope.empty {
            val x = intVar("x", 0..9)
            create().use { solver ->
                solver.openSession(buildModel()).use { session ->
                    session.optimize(Objective.Maximize(x), listOf(x le 4)).objectiveValue shouldBe BigInteger.of(4)
                    session.optimize(Objective.Maximize(x)).objectiveValue shouldBe BigInteger.of(9)
                    session.optimize(Objective.Minimize(x)).objectiveValue shouldBe BigInteger.ZERO
                    session.check(listOf(x eq 7)).status shouldBe CheckStatus.SATISFIABLE
                }
            }
        }
    }

    test("incremental-optimization/logic-and-optimization-interleave-with-checks") {
        ConstraintScope.empty {
            val x = intVar("x", 0..9)
            add(BoolOr(x le 2, x ge 7))
            create().use { solver ->
                solver.openSession(buildModel()).use { session ->
                    session.optimize(Objective.Maximize(x), listOf(x le 6)).objectiveValue shouldBe BigInteger.TWO
                    session.check(listOf(x eq 5)).status shouldBe CheckStatus.UNSATISFIABLE
                    session.optimize(Objective.Maximize(x)).objectiveValue shouldBe BigInteger.of(9)
                    val before = session.push()
                    session.add(x neq 9)
                    session.optimize(Objective.Maximize(x)).objectiveValue shouldBe BigInteger.of(8)
                    session.popTo(before)
                    session.optimize(Objective.Minimize(x), listOf(x ge 3)).objectiveValue shouldBe BigInteger.of(7)
                }
            }
        }
    }

    test("incremental-optimization/declared-variables-join-objectives-and-answers") {
        ConstraintScope.empty {
            val x = intVar("x", 0..3)
            create().use { solver ->
                solver.openSession(buildModel()).use { session ->
                    val z = VariableFactory.empty().intVar("z", IntDomain.of(0, 5))
                    session.declare(z)
                    session.add(z le x)
                    val result = session.optimize(Objective.Maximize(z))
                    result.objectiveValue shouldBe BigInteger.of(3)
                    result.assignment?.valueOf(z) shouldBe BigInteger.of(3)
                    result.assignment?.valueOf(x) shouldBe BigInteger.of(3)
                }
            }
        }
    }

    test("incremental-optimization/infeasible-and-zero-timeout") {
        ConstraintScope.empty {
            val x = intVar("x", 0..9)
            create().use { solver ->
                solver.openSession(buildModel()).use { session ->
                    session.optimize(Objective.Maximize(x), listOf(x ge 10)).status shouldBe SolveStatus.INFEASIBLE
                    val stopped = session.optimize(Objective.Maximize(x), timeoutMillis = 0)
                    stopped.status shouldBe SolveStatus.UNKNOWN
                    stopped.terminationReason shouldBe TerminationReason.TIMEOUT
                    session.optimize(Objective.Maximize(x)).objectiveValue shouldBe BigInteger.of(9)
                }
            }
        }
    }

    test("incremental-optimization/real-objectives-are-exact").config(enabled = realObjectives) {
        ConstraintScope.empty {
            val r = realVar("r")
            val s = realVar("s")
            add(r + s le Rational.of(3, 2))
            add(s ge 0)
            create().use { solver ->
                solver.openSession(buildModel()).use { session ->
                    val first = session.optimize(Objective.MaximizeReal(r))
                    first.status shouldBe SolveStatus.OPTIMAL
                    first.realObjectiveValue shouldBe Rational.of(3, 2)
                    val before = session.push()
                    session.add(s ge Rational.of(1, 3))
                    session.optimize(Objective.MaximizeReal(r)).realObjectiveValue shouldBe Rational.of(7, 6)
                    session.popTo(before)
                    session.optimize(Objective.MinimizeReal(r)).status shouldBe SolveStatus.UNBOUNDED
                    session.optimize(Objective.MaximizeReal(r + s), listOf(r le 1)).realObjectiveValue shouldBe Rational.of(3, 2)
                }
            }
        }
    }
}
