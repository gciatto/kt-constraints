package io.github.gciatto.ktconstraints.solve.z3

import io.github.gciatto.ktconstraints.core.Rational
import io.github.gciatto.ktconstraints.core.integer.IntToReal
import io.github.gciatto.ktconstraints.core.real.RealLiteral
import io.github.gciatto.ktconstraints.core.real.arithmetic.RealDifference
import io.github.gciatto.ktconstraints.core.real.arithmetic.RealScale
import io.github.gciatto.ktconstraints.core.real.arithmetic.RealSum
import io.github.gciatto.ktconstraints.core.real.relation.RealEquality
import io.github.gciatto.ktconstraints.core.real.relation.RealLessThan
import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.model.ConstraintScope
import io.github.gciatto.ktconstraints.solve.incremental.CheckStatus
import io.github.gciatto.ktconstraints.solve.incremental.IncrementalConstraintSolver
import io.github.gciatto.ktconstraints.solve.result.SolveStatus
import io.kotest.core.factory.TestFactory
import io.kotest.core.spec.style.funSpec
import io.kotest.matchers.shouldBe
import org.gciatto.kt.math.BigInteger

/** Exact linear real arithmetic shared by every Z3 route: rational models, never rounded values. */
fun z3RealContract(create: () -> IncrementalConstraintSolver): TestFactory =
    funSpec {
        test("z3-real/fractional-solution-is-exact") {
            ConstraintScope.empty {
                val x = realVar("x")
                val y = realVar("y")
                add(RealEquality(RealSum(x, y), RealLiteral(Rational.ONE)))
                add(RealEquality(RealDifference(x, y), RealLiteral(Rational.of(1, 3))))
                create().use { solver ->
                    val result = solver.solve(buildModel())
                    result.status shouldBe SolveStatus.FEASIBLE
                    result.assignment?.valueOf(x) shouldBe Rational.of(2, 3)
                    result.assignment?.valueOf(y) shouldBe Rational.of(1, 3)
                }
            }
        }

        test("z3-real/negative-and-integral-numerals-round-trip") {
            ConstraintScope.empty {
                val x = realVar("x")
                val y = realVar("y")
                add(RealEquality(x, RealLiteral(Rational.of(-7, 3))))
                add(RealEquality(y, RealScale(Rational.of(-3), x)))
                create().use { solver ->
                    val result = solver.solve(buildModel())
                    result.assignment?.valueOf(x) shouldBe Rational.of(-7, 3)
                    result.assignment?.valueOf(y) shouldBe Rational.of(7)
                }
            }
        }

        test("z3-real/strict-bounds-are-unsatisfiable") {
            val model =
                ConstraintModel.of {
                    val x = realVar("x")
                    add(RealLessThan(x, RealLiteral(Rational.ONE)))
                    add(RealLessThan(RealLiteral(Rational.ONE), x))
                }
            create().use { solver -> solver.solve(model).status shouldBe SolveStatus.INFEASIBLE }
        }

        test("z3-real/integer-conversion-keeps-integrality") {
            val oddModel =
                ConstraintModel.of {
                    val i = intVar("i", 0..10)
                    add(RealEquality(RealScale(Rational.of(2), IntToReal(i)), RealLiteral(Rational.of(3))))
                }
            ConstraintScope.empty {
                val j = intVar("j", 0..10)
                add(RealEquality(RealScale(Rational.of(1, 2), IntToReal(j)), RealLiteral(Rational.of(3, 2))))
                create().use { solver ->
                    solver.solve(oddModel).status shouldBe SolveStatus.INFEASIBLE
                    solver.solve(buildModel()).assignment?.valueOf(j) shouldBe BigInteger.of(3)
                }
            }
        }

        test("z3-real/huge-rationals-are-exact") {
            val huge = BigInteger.of("1000000000000000000000000000001")
            ConstraintScope.empty {
                val x = realVar("x")
                add(RealEquality(RealScale(Rational.of(3), x), RealLiteral(Rational.of(huge))))
                create().use { solver -> solver.solve(buildModel()).assignment?.valueOf(x) shouldBe Rational.of(huge, BigInteger.of(3)) }
            }
        }

        test("z3-real/sessions-check-real-assumptions") {
            ConstraintScope.empty {
                val x = realVar("x")
                add(RealLessThan(RealLiteral(Rational.ZERO), x))
                val negative = RealLessThan(x, RealLiteral(Rational.ZERO))
                create().use { solver ->
                    solver.openSession(buildModel()).use { session ->
                        val refuted = session.check(listOf(negative))
                        refuted.status shouldBe CheckStatus.UNSATISFIABLE
                        refuted.unsatCore shouldBe listOf(negative)
                        session.add(RealLessThan(x, RealLiteral(Rational.of(1, 1000))))
                        val found = session.check()
                        found.status shouldBe CheckStatus.SATISFIABLE
                        val value = requireNotNull(found.assignment?.valueOf(x))
                        (Rational.ZERO < value && value < Rational.of(1, 1000)) shouldBe true
                    }
                }
            }
        }
    }
