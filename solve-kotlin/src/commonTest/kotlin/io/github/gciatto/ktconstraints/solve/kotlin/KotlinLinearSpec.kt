package io.github.gciatto.ktconstraints.solve.kotlin

import io.github.gciatto.ktconstraints.core.BoolNot
import io.github.gciatto.ktconstraints.core.BoolOr
import io.github.gciatto.ktconstraints.core.IntDomain
import io.github.gciatto.ktconstraints.core.IntEquality
import io.github.gciatto.ktconstraints.core.IntLessOrEqual
import io.github.gciatto.ktconstraints.core.IntLessThan
import io.github.gciatto.ktconstraints.core.IntLiteral
import io.github.gciatto.ktconstraints.core.IntMax
import io.github.gciatto.ktconstraints.core.IntNegation
import io.github.gciatto.ktconstraints.core.IntSum
import io.github.gciatto.ktconstraints.core.LinearSum
import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.model.ConstraintScope
import io.github.gciatto.ktconstraints.test.solve.FiniteOracle
import io.github.gciatto.ktconstraints.test.solve.enumerate
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.gciatto.kt.math.BigInteger

/** Linear sums compared natively, in every Boolean context, agree with the finite oracle. */
class KotlinLinearSpec :
    FunSpec({
        suspend fun agrees(model: ConstraintModel) {
            val optimizing = model.objective != null
            val (expected, oracle) = enumerate(FiniteOracle(), model, objectives = optimizing)
            KotlinBackend().create().use { solver ->
                val (actual, result) = enumerate(solver, model, objectives = optimizing)
                result.status shouldBe oracle.status
                if (optimizing) result.objectiveValue shouldBe oracle.objectiveValue else actual.toSet() shouldBe expected.toSet()
            }
        }

        fun coefficients(vararg values: Long) = values.map { BigInteger.of(it) }

        test("kotlin-linear/negated-and-strict-sums-with-negative-coefficients") {
            val scope = ConstraintScope.empty()
            val xs = listOf(scope.intVar("x", -2..3), scope.intVar("y", IntDomain.values(-4, 0, 5)), scope.intVar("z", 0..2))
            scope.add(BoolNot(IntLessOrEqual(LinearSum(xs, coefficients(3, -2, 5), BigInteger.of(1)), IntLiteral(4))))
            scope.add(IntLessThan(LinearSum(xs, coefficients(-1, 1, 2)), IntLiteral(6)))
            agrees(scope.build())
        }

        test("kotlin-linear/equalities-mix-linear-sums-with-plain-integer-terms") {
            val scope = ConstraintScope.empty()
            val x = scope.intVar("x", 0..4)
            val y = scope.intVar("y", -3..3)
            val z = scope.intVar("z", 0..6)
            scope.add(
                IntEquality(IntSum(LinearSum(listOf(x, y), coefficients(2, -1)), z), IntNegation(LinearSum(listOf(y), coefficients(3)))),
            )
            scope.add(
                BoolOr(
                    IntEquality(LinearSum(listOf(x, z), coefficients(1, 1)), IntLiteral(5)),
                    IntLessOrEqual(IntMax(listOf(x, y)), IntLiteral(0)),
                ),
            )
            agrees(scope.build())
        }

        test("kotlin-linear/reified-capacities-and-a-linear-objective-reach-the-optimum") {
            val scope = ConstraintScope.empty()
            val take = List(5) { scope.intVar("take$it", 0..2) }
            val heavy = scope.boolVar("heavy")
            scope.add(IntLessOrEqual(LinearSum(take, coefficients(4, 3, 5, 2, 6)), IntLiteral(14)))
            scope.add(BoolOr(heavy, IntLessOrEqual(LinearSum(take, coefficients(1, 1, 1, 1, 1)), IntLiteral(3))))
            scope.add(BoolOr(BoolNot(heavy), IntLessOrEqual(LinearSum(take, coefficients(0, 2, 0, 3, 1)), IntLiteral(2))))
            scope.maximize(LinearSum(take, coefficients(7, 5, 9, 3, 8), BigInteger.of(-2)))
            agrees(scope.build())
        }
    })
