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
            val scope = ConstraintScope.empty()
            val x = scope.intVar("x", 0..10)
            val y = scope.intVar("y", 0..10)
            scope.add(BoolAnd(IntEquality(IntSum(x, y), IntLiteral(10)), IntLessOrEqual(x, IntLiteral(3))))
            val model = scope.build()

            val result = IntegerPresolver().run(model)

            result.conflict shouldBe false
            result.fixedPoint shouldBe true
            result.domains[x] shouldBe IntDomain.of(0, 3)
            result.domains[y] shouldBe IntDomain.of(7, 10)
            result.reductions.all { it.sourceAssertion == 0 } shouldBe true
            x.domain shouldBe IntDomain.of(0, 10)
            y.domain shouldBe IntDomain.of(0, 10)
        }

        test("presolve/negative-division-uses-mathematical-floor-and-ceiling") {
            val scope = ConstraintScope.empty()
            val x = scope.intVar("x", -10..10)
            val y = scope.intVar("y", -10..10)
            scope.add(IntLessOrEqual(IntProduct(IntLiteral(-3), x), IntLiteral(7)))
            scope.add(IntLessOrEqual(IntProduct(IntLiteral(3), y), IntLiteral(-7)))

            val result = IntegerPresolver().run(scope.build())

            result.domains[x] shouldBe IntDomain.of(-2, 10)
            result.domains[y] shouldBe IntDomain.of(-10, -3)
        }

        test("presolve/strict-order-and-sparse-domains-keep-the-exact-values") {
            val scope = ConstraintScope.empty()
            val x = scope.intVar("x", IntDomain.values(0, 3, 9))
            scope.add(IntLessThan(IntLiteral(0), x))
            scope.add(IntLessOrEqual(x, IntLiteral(6)))

            IntegerPresolver().run(scope.build()).domains[x] shouldBe IntDomain.values(3)
        }

        test("presolve/nonintegral-equalities-can-prove-a-conflict") {
            val scope = ConstraintScope.empty()
            val x = scope.intVar("x", -10..10)
            scope.add(IntEquality(IntProduct(IntLiteral(3), x), IntLiteral(7)))

            IntegerPresolver().run(scope.build()).conflict shouldBe true
        }

        test("presolve/large-exact-coefficients-and-a-fixed-product-factor") {
            val scope = ConstraintScope.empty()
            val factor = BigInteger.TEN.pow(80)
            val x = scope.intVar("x", -10..10)
            val k = scope.intVar("k", IntDomain.of(factor, factor))
            scope.add(IntLessOrEqual(IntProduct(k, x), IntLiteral(factor * 3 + 1)))

            IntegerPresolver().run(scope.build()).domains[x] shouldBe IntDomain.of(-10, 3)
        }

        test("presolve/assigned-all-different-values-are-removed-without-enumeration") {
            val scope = ConstraintScope.empty()
            val x = scope.intVar("x", 0..4)
            val y = scope.intVar("y", 1..1)
            scope.add(AllDifferent(listOf(x, y, IntLiteral(3))))

            IntegerPresolver().run(scope.build()).domains[x] shouldBe IntDomain.values(0, 2, 4)
        }

        test("presolve/repeated-fixed-all-different-values-prove-a-conflict") {
            val scope = ConstraintScope.empty()
            val x = scope.intVar("x", 2..2)
            scope.add(AllDifferent(listOf(x, IntLiteral(2))))

            IntegerPresolver().run(scope.build()).conflict shouldBe true
        }

        test("presolve/more-all-different-operands-than-values-prove-a-conflict") {
            val scope = ConstraintScope.empty()
            val pigeons = List(4) { scope.intVar("p$it", IntDomain.values(0, 5, 9)) }
            scope.add(AllDifferent(pigeons))

            IntegerPresolver().run(scope.build()).conflict shouldBe true
        }

        test("presolve/all-different-with-enough-values-across-holes-is-not-a-conflict") {
            val scope = ConstraintScope.empty()
            val x = scope.intVar("x", IntDomain.values(0, 5))
            val y = scope.intVar("y", IntDomain.values(0, 5))
            val huge = scope.intVar("huge", IntDomain.of(0L, Long.MAX_VALUE))
            scope.add(AllDifferent(listOf(x, y, huge, IntLiteral(7))))

            IntegerPresolver().run(scope.build()).conflict shouldBe false
        }

        test("presolve/does-not-assume-an-inactive-disjunct-or-a-nonlinear-product") {
            val scope = ConstraintScope.empty()
            val x = scope.intVar("x", 0..10)
            val y = scope.intVar("y", 0..10)
            scope.add(BoolOr(IntEquality(x, IntLiteral(0)), IntEquality(x, IntLiteral(10))))
            scope.add(IntEquality(IntProduct(x, y), IntLiteral(0)))

            val result = IntegerPresolver().run(scope.build())

            result.conflict shouldBe false
            result.domains[x] shouldBe x.domain
            result.domains[y] shouldBe y.domain
        }

        test("presolve/constant-false-and-initial-empty-domain-are-conflicts") {
            val falseScope = ConstraintScope.empty()
            falseScope.add(BoolLiteral(false))
            IntegerPresolver().run(falseScope.build()).conflict shouldBe true
            val emptyScope = ConstraintScope.empty()
            emptyScope.intVar("x", IntDomain.empty())
            IntegerPresolver().run(emptyScope.build()).conflict shouldBe true
        }

        test("presolve/pass-budget-stops-conservatively-on-slow-bound-cycles") {
            val scope = ConstraintScope.empty()
            val limit = BigInteger.TEN.pow(100)
            val x = scope.intVar("x", IntDomain.of(BigInteger.ZERO, limit))
            val y = scope.intVar("y", IntDomain.of(BigInteger.ZERO, limit))
            scope.add(IntLessThan(x, y))
            scope.add(IntLessThan(y, x))

            val result = IntegerPresolver(maxPasses = 1).run(scope.build())

            result.conflict shouldBe false
            result.fixedPoint shouldBe false
        }

        test("presolve/checkpoints-allow-cancellation-without-model-mutation") {
            val scope = ConstraintScope.empty()
            val x = scope.intVar("x", 0..10)
            scope.add(IntEquality(x, IntLiteral(2)))
            val model = scope.build()
            var checks = 0

            shouldThrow<IllegalStateException> {
                IntegerPresolver().run(model) {
                    checks++
                    if (checks >= 2) error("cancelled")
                }
            }
            x.domain shouldBe IntDomain.of(0, 10)
        }

        test("presolve/small-affine-inequalities-preserve-every-arithmetic-witness") {
            for (a in -3..3) {
                for (b in -3..3) {
                    for (bound in -4..4) {
                        val scope = ConstraintScope.empty()
                        val x = scope.intVar("x", -2..2)
                        val y = scope.intVar("y", -2..2)
                        val sum = LinearSum(listOf(x, y), listOf(BigInteger.of(a), BigInteger.of(b)))
                        scope.add(IntLessOrEqual(sum, IntLiteral(bound)))
                        val result = IntegerPresolver().run(scope.build())
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

        test("presolve/repeated-variables-cancel-exactly-in-affine-expressions") {
            val scope = ConstraintScope.empty()
            val x = scope.intVar("x", -10..10)
            scope.add(IntLessThan(IntDifference(x, x), IntLiteral(0)))

            IntegerPresolver().run(scope.build()).conflict shouldBe true
        }
    })
