package io.github.gciatto.ktconstraints.solve.z3

import io.github.gciatto.ktconstraints.core.IntToReal
import io.github.gciatto.ktconstraints.core.Rational
import io.github.gciatto.ktconstraints.core.RealDifference
import io.github.gciatto.ktconstraints.core.RealEquality
import io.github.gciatto.ktconstraints.core.RealLessThan
import io.github.gciatto.ktconstraints.core.RealLiteral
import io.github.gciatto.ktconstraints.core.RealScale
import io.github.gciatto.ktconstraints.core.RealSum
import io.github.gciatto.ktconstraints.model.ConstraintScope
import io.github.gciatto.ktconstraints.solve.CheckStatus
import io.github.gciatto.ktconstraints.solve.IncrementalConstraintSolver
import io.github.gciatto.ktconstraints.solve.SolveStatus
import io.kotest.core.factory.TestFactory
import io.kotest.core.spec.style.funSpec
import io.kotest.matchers.shouldBe
import org.gciatto.kt.math.BigInteger

/** Exact linear real arithmetic shared by every Z3 route: rational models, never rounded values. */
fun z3RealContract(create: () -> IncrementalConstraintSolver): TestFactory =
    funSpec {
        test("z3-real/fractional-solution-is-exact") {
            val scope = ConstraintScope.empty()
            val x = scope.realVar("x")
            val y = scope.realVar("y")
            scope.add(RealEquality(RealSum(x, y), RealLiteral(Rational.ONE)))
            scope.add(RealEquality(RealDifference(x, y), RealLiteral(Rational.of(1, 3))))
            create().use { solver ->
                val result = solver.solve(scope.build())
                result.status shouldBe SolveStatus.FEASIBLE
                result.assignment?.valueOf(x) shouldBe Rational.of(2, 3)
                result.assignment?.valueOf(y) shouldBe Rational.of(1, 3)
            }
        }

        test("z3-real/strict-bounds-are-unsatisfiable") {
            val scope = ConstraintScope.empty()
            val x = scope.realVar("x")
            scope.add(RealLessThan(x, RealLiteral(Rational.ONE)))
            scope.add(RealLessThan(RealLiteral(Rational.ONE), x))
            create().use { solver -> solver.solve(scope.build()).status shouldBe SolveStatus.INFEASIBLE }
        }

        test("z3-real/integer-conversion-keeps-integrality") {
            val odd = ConstraintScope.empty()
            val i = odd.intVar("i", 0..10)
            odd.add(RealEquality(RealScale(Rational.of(2), IntToReal(i)), RealLiteral(Rational.of(3))))
            val half = ConstraintScope.empty()
            val j = half.intVar("j", 0..10)
            half.add(RealEquality(RealScale(Rational.of(1, 2), IntToReal(j)), RealLiteral(Rational.of(3, 2))))
            create().use { solver ->
                solver.solve(odd.build()).status shouldBe SolveStatus.INFEASIBLE
                solver.solve(half.build()).assignment?.valueOf(j) shouldBe BigInteger.of(3)
            }
        }

        test("z3-real/huge-rationals-are-exact") {
            val huge = BigInteger.of("1000000000000000000000000000001")
            val scope = ConstraintScope.empty()
            val x = scope.realVar("x")
            scope.add(RealEquality(RealScale(Rational.of(3), x), RealLiteral(Rational.of(huge))))
            create().use { solver -> solver.solve(scope.build()).assignment?.valueOf(x) shouldBe Rational.of(huge, BigInteger.of(3)) }
        }

        test("z3-real/sessions-check-real-assumptions") {
            val scope = ConstraintScope.empty()
            val x = scope.realVar("x")
            scope.add(RealLessThan(RealLiteral(Rational.ZERO), x))
            val negative = RealLessThan(x, RealLiteral(Rational.ZERO))
            create().use { solver ->
                solver.openSession(scope.build()).use { session ->
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
