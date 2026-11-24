package io.github.gciatto.ktconstraints.solve.kotlin.cp

import io.github.gciatto.ktconstraints.core.bool.BoolAnd
import io.github.gciatto.ktconstraints.core.bool.BoolLiteral
import io.github.gciatto.ktconstraints.core.bool.BoolOr
import io.github.gciatto.ktconstraints.core.domain.IntDomain
import io.github.gciatto.ktconstraints.core.global.counting.AllDifferent
import io.github.gciatto.ktconstraints.core.integer.IntLiteral
import io.github.gciatto.ktconstraints.core.integer.arithmetic.IntDifference
import io.github.gciatto.ktconstraints.core.integer.arithmetic.IntProduct
import io.github.gciatto.ktconstraints.core.integer.arithmetic.IntSum
import io.github.gciatto.ktconstraints.core.integer.arithmetic.LinearSum
import io.github.gciatto.ktconstraints.core.integer.relation.IntEquality
import io.github.gciatto.ktconstraints.core.integer.relation.IntLessOrEqual
import io.github.gciatto.ktconstraints.core.integer.relation.IntLessThan
import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.model.ConstraintScope
import io.github.gciatto.ktconstraints.solve.kotlin.cp.presolve.IntegerPresolver
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.gciatto.kt.math.BigInteger

/** Bounds propagation checked against independent finite arithmetic witnesses. */
class IntegerPresolverSpec :
    FunSpec({
        test("presolve/equality-and-conjunction-reach-a-fixed-point") {
            ConstraintScope.empty {
                val x = intVar("x", 0..10)
                val y = intVar("y", 0..10)
                add(BoolAnd(IntEquality(IntSum(x, y), IntLiteral(10)), IntLessOrEqual(x, IntLiteral(3))))
                val model = buildModel()

                val result = IntegerPresolver().run(model)

                result.conflict shouldBe false
                result.fixedPoint shouldBe true
                result.domains[x] shouldBe IntDomain.of(0, 3)
                result.domains[y] shouldBe IntDomain.of(7, 10)
                result.reductions.all { it.sourceAssertion == 0 } shouldBe true
                x.domain shouldBe IntDomain.of(0, 10)
                y.domain shouldBe IntDomain.of(0, 10)
            }
        }

        test("presolve/negative-division-uses-mathematical-floor-and-ceiling") {
            ConstraintScope.empty {
                val x = intVar("x", -10..10)
                val y = intVar("y", -10..10)
                add(IntLessOrEqual(IntProduct(IntLiteral(-3), x), IntLiteral(7)))
                add(IntLessOrEqual(IntProduct(IntLiteral(3), y), IntLiteral(-7)))

                val result = IntegerPresolver().run(buildModel())

                result.domains[x] shouldBe IntDomain.of(-2, 10)
                result.domains[y] shouldBe IntDomain.of(-10, -3)
            }
        }

        test("presolve/strict-order-and-sparse-domains-keep-the-exact-values") {
            ConstraintScope.empty {
                val x = intVar("x", IntDomain.values(0, 3, 9))
                add(IntLessThan(IntLiteral(0), x))
                add(IntLessOrEqual(x, IntLiteral(6)))

                IntegerPresolver().run(buildModel()).domains[x] shouldBe IntDomain.values(3)
            }
        }

        test("presolve/nonintegral-equalities-can-prove-a-conflict") {
            val model =
                ConstraintModel.of {
                    val x = intVar("x", -10..10)
                    add(IntEquality(IntProduct(IntLiteral(3), x), IntLiteral(7)))
                }
            IntegerPresolver().run(model).conflict shouldBe true
        }

        test("presolve/large-exact-coefficients-and-a-fixed-product-factor") {
            ConstraintScope.empty {
                val factor = BigInteger.TEN.pow(80)
                val x = intVar("x", -10..10)
                val k = intVar("k", IntDomain.of(factor, factor))
                add(IntLessOrEqual(IntProduct(k, x), IntLiteral(factor * 3 + 1)))

                IntegerPresolver().run(buildModel()).domains[x] shouldBe IntDomain.of(-10, 3)
            }
        }

        test("presolve/assigned-all-different-values-are-removed-without-enumeration") {
            ConstraintScope.empty {
                val x = intVar("x", 0..4)
                val y = intVar("y", 1..1)
                add(AllDifferent(listOf(x, y, IntLiteral(3))))

                IntegerPresolver().run(buildModel()).domains[x] shouldBe IntDomain.values(0, 2, 4)
            }
        }

        test("presolve/repeated-fixed-all-different-values-prove-a-conflict") {
            val model =
                ConstraintModel.of {
                    val x = intVar("x", 2..2)
                    add(AllDifferent(listOf(x, IntLiteral(2))))
                }
            IntegerPresolver().run(model).conflict shouldBe true
        }

        test("presolve/more-all-different-operands-than-values-prove-a-conflict") {
            val model =
                ConstraintModel.of {
                    val pigeons = List(4) { intVar("p$it", IntDomain.values(0, 5, 9)) }
                    add(AllDifferent(pigeons))
                }
            IntegerPresolver().run(model).conflict shouldBe true
        }

        test("presolve/all-different-with-enough-values-across-holes-is-not-a-conflict") {
            val model =
                ConstraintModel.of {
                    val x = intVar("x", IntDomain.values(0, 5))
                    val y = intVar("y", IntDomain.values(0, 5))
                    val huge = intVar("huge", IntDomain.of(0L, Long.MAX_VALUE))
                    add(AllDifferent(listOf(x, y, huge, IntLiteral(7))))
                }
            IntegerPresolver().run(model).conflict shouldBe false
        }

        test("presolve/does-not-assume-an-inactive-disjunct-or-a-nonlinear-product") {
            ConstraintScope.empty {
                val x = intVar("x", 0..10)
                val y = intVar("y", 0..10)
                add(BoolOr(IntEquality(x, IntLiteral(0)), IntEquality(x, IntLiteral(10))))
                add(IntEquality(IntProduct(x, y), IntLiteral(0)))

                val result = IntegerPresolver().run(buildModel())

                result.conflict shouldBe false
                result.domains[x] shouldBe x.domain
                result.domains[y] shouldBe y.domain
            }
        }

        test("presolve/constant-false-and-initial-empty-domain-are-conflicts") {
            val scopeModel =
                ConstraintModel.of {
                    add(BoolLiteral(false))
                }
            IntegerPresolver().run(scopeModel).conflict shouldBe true
            val model =
                ConstraintModel.of {
                    intVar("x", IntDomain.empty())
                }
            IntegerPresolver().run(model).conflict shouldBe true
        }

        test("presolve/pass-budget-stops-conservatively-on-slow-bound-cycles") {
            val model =
                ConstraintModel.of {
                    val limit = BigInteger.TEN.pow(100)
                    val x = intVar("x", IntDomain.of(BigInteger.ZERO, limit))
                    val y = intVar("y", IntDomain.of(BigInteger.ZERO, limit))
                    add(IntLessThan(x, y))
                    add(IntLessThan(y, x))
                }
            val result = IntegerPresolver(maxPasses = 1).run(model)

            result.conflict shouldBe false
            result.fixedPoint shouldBe false
        }

        test("presolve/checkpoints-allow-cancellation-without-model-mutation") {
            ConstraintScope.empty {
                val x = intVar("x", 0..10)
                add(IntEquality(x, IntLiteral(2)))
                val model = buildModel()
                var checks = 0

                shouldThrow<IllegalStateException> {
                    IntegerPresolver().run(model) {
                        checks++
                        if (checks >= 2) error("cancelled")
                    }
                }
                x.domain shouldBe IntDomain.of(0, 10)
            }
        }

        test("presolve/small-affine-inequalities-preserve-every-arithmetic-witness") {
            for (a in -3..3) {
                for (b in -3..3) {
                    for (bound in -4..4) {
                        ConstraintScope.empty {
                            val x = intVar("x", -2..2)
                            val y = intVar("y", -2..2)
                            val sum = LinearSum(listOf(x, y), listOf(BigInteger.of(a), BigInteger.of(b)))
                            add(IntLessOrEqual(sum, IntLiteral(bound)))
                            val result = IntegerPresolver().run(buildModel())
                            var witnesses = 0
                            for (vx in -2..2) {
                                for (vy in -2..2) {
                                    if (a * vx + b * vy <= bound) {
                                        witnesses++
                                        (BigInteger.of(vx) in requireNotNull(result.domains[x])) shouldBe true
                                        (BigInteger.of(vy) in requireNotNull(result.domains[y])) shouldBe true
                                    }
                                }
                            }
                            if (result.conflict) witnesses shouldBe 0
                        }
                    }
                }
            }
        }

        test("presolve/repeated-variables-cancel-exactly-in-affine-expressions") {
            val model =
                ConstraintModel.of {
                    val x = intVar("x", -10..10)
                    add(IntLessThan(IntDifference(x, x), IntLiteral(0)))
                }
            IntegerPresolver().run(model).conflict shouldBe true
        }
    })
