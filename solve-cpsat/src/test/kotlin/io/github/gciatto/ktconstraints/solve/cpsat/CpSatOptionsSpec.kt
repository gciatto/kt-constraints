package io.github.gciatto.ktconstraints.solve.cpsat

import io.github.gciatto.ktconstraints.core.global.counting.AllDifferent
import io.github.gciatto.ktconstraints.core.integer.IntLiteral
import io.github.gciatto.ktconstraints.core.integer.arithmetic.IntSum
import io.github.gciatto.ktconstraints.core.integer.arithmetic.LinearSum
import io.github.gciatto.ktconstraints.core.integer.relation.IntLessOrEqual
import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.model.ConstraintScope
import io.github.gciatto.ktconstraints.solve.SolveOptions
import io.github.gciatto.ktconstraints.solve.assignment.Assignment
import io.github.gciatto.ktconstraints.solve.result.SolveStatus
import io.github.gciatto.ktconstraints.test.solve.oracle.enumerate
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.gciatto.kt.math.BigInteger

/** Per-solve CP-SAT tuning never changes answers: workers, seeds and (even infeasible) hints. */
class CpSatOptionsSpec :
    FunSpec({
        test("cp-sat-options/tuned-optimization-keeps-the-proven-optimum") {
            ConstraintScope.empty {
                val xs = List(4) { intVar("x$it", 0..5) }
                add(AllDifferent(xs))
                add(IntLessOrEqual(IntSum(xs[0], xs[1]), IntLiteral(6)))
                maximize(LinearSum(xs, listOf(3, 1, 2, 1).map { BigInteger.of(it) }))
                val model = buildModel()
                val hints = Assignment.of(integers = mapOf(xs[0] to BigInteger.ZERO, xs[1] to BigInteger.ZERO))
                val tuned = SolveOptions() + CpSatOptions(workers = 2, randomSeed = 7, hints = hints)
                val plain = CpSatBackend().create().use { it.solve(model) }
                val result = CpSatBackend().create().use { it.solve(model, tuned) }
                result.status shouldBe SolveStatus.OPTIMAL
                result.objectiveValue shouldBe plain.objectiveValue
            }
        }

        test("cp-sat-options/enumeration-stays-complete-with-workers") {
            val model =
                ConstraintModel.of {
                    val xs = List(3) { intVar("x$it", 0..2) }
                    add(AllDifferent(xs))
                }
            val (found, result) = CpSatBackend().create().use { enumerate(it, model, SolveOptions() + CpSatOptions(workers = 4)) }
            found.toSet().size shouldBe 6
            result.status shouldBe SolveStatus.FEASIBLE
        }

        test("cp-sat-options/validation") {
            shouldThrow<IllegalArgumentException> { CpSatOptions(workers = -1) }
        }
    })
