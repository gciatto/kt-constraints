package io.github.gciatto.ktconstraints.solve.kotlin.compiler

import io.github.gciatto.ktconstraints.core.Rational
import io.github.gciatto.ktconstraints.core.bool.BoolNot
import io.github.gciatto.ktconstraints.core.domain.IntDomain
import io.github.gciatto.ktconstraints.core.integer.IntLiteral
import io.github.gciatto.ktconstraints.core.integer.IntToReal
import io.github.gciatto.ktconstraints.core.integer.relation.IntEquality
import io.github.gciatto.ktconstraints.core.real.RealLiteral
import io.github.gciatto.ktconstraints.core.real.arithmetic.RealScale
import io.github.gciatto.ktconstraints.core.real.arithmetic.RealSum
import io.github.gciatto.ktconstraints.core.real.relation.RealEquality
import io.github.gciatto.ktconstraints.core.real.relation.RealLessOrEqual
import io.github.gciatto.ktconstraints.core.real.relation.RealLessThan
import io.github.gciatto.ktconstraints.model.ConstraintScope
import io.github.gciatto.ktconstraints.solve.kotlin.sat.SatStatus
import io.github.gciatto.ktconstraints.solve.kotlin.search.SearchBudget
import io.github.gciatto.ktconstraints.solve.kotlin.search.SearchStopped
import io.github.gciatto.ktconstraints.solve.result.TerminationReason
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.gciatto.kt.math.BigInteger

class DiscreteRealPredicateSpec :
    FunSpec({
        test("integer-only real bounds above Long are decided by SAT without theory enumeration") {
            val fixture = TranslationFixture()
            with(ConstraintScope.empty()) {
                val x = intVar("x", IntDomain.of(BigInteger.ZERO, BigInteger.ONE shl 80))
                fixture.declare(this)
                val bound = (BigInteger.ONE shl 70) + 3
                val predicate = RealLessOrEqual(IntToReal(x), RealLiteral(Rational.of(bound)))
                val literal = fixture.translation.assertion(predicate, fixture.budget)

                fixture.translation.realAtoms.size shouldBe 0
                for (value in listOf(bound - 1, bound, bound + 1)) {
                    val fixed = fixture.translation.assertion(IntEquality(x, IntLiteral(value)), fixture.budget)
                    val expected = value <= bound
                    val accepted = if (expected) literal else -literal
                    fixture.solver.solve(intArrayOf(fixed, accepted)).status shouldBe SatStatus.SATISFIABLE
                    fixture.solver.solve(intArrayOf(fixed, -accepted)).status shouldBe SatStatus.UNSATISFIABLE
                }
            }
        }

        test("rational coefficients retain strictness equality and negation after denominator scaling") {
            val fixture = TranslationFixture()
            with(ConstraintScope.empty()) {
                val x = intVar("x", -4..4)
                val y = intVar("y", -3..3)
                fixture.declare(this)
                val expression =
                    RealSum(
                        RealScale(Rational.of(2, 3), IntToReal(x)),
                        RealScale(Rational.of(-5, 7), IntToReal(y)),
                    )
                val bound = RealLiteral(Rational.of(11, 21))
                val equal = RealEquality(expression, bound)
                val predicates = listOf(RealLessThan(expression, bound), RealLessOrEqual(expression, bound), equal, BoolNot(equal))
                val literals = predicates.map { fixture.translation.assertion(it, fixture.budget) }

                fixture.translation.realAtoms.size shouldBe 0
                for ((first, second) in listOf(-4 to -3, 0 to 0, 1 to 0, 4 to 3, -1 to 1)) {
                    val fixed =
                        intArrayOf(
                            fixture.translation.assertion(IntEquality(x, IntLiteral(first)), fixture.budget),
                            fixture.translation.assertion(IntEquality(y, IntLiteral(second)), fixture.budget),
                        )
                    val scaled = 14 * first - 15 * second
                    val expected = listOf(scaled < 11, scaled <= 11, scaled == 11, scaled != 11)
                    for (index in literals.indices) {
                        val accepted = if (expected[index]) literals[index] else -literals[index]
                        fixture.solver.solve(fixed + accepted).status shouldBe SatStatus.SATISFIABLE
                        fixture.solver.solve(fixed + (-accepted)).status shouldBe SatStatus.UNSATISFIABLE
                    }
                }
            }
        }

        test("denominator scaling observes the work budget and can be retried") {
            val fixture = TranslationFixture()
            with(ConstraintScope.empty()) {
                val x = intVar("x", IntDomain.of(BigInteger.ZERO, (BigInteger.ONE shl 128) - 1))
                fixture.declare(this)
                val left = IntToReal(x)
                val right = RealLiteral(Rational.of(1, 3))
                fixture.translation.translate(left, fixture.budget)
                fixture.translation.translate(right, fixture.budget)
                val predicate = RealLessThan(left, right)
                val limited = SearchBudget(null, 30) { false }
                fixture.circuit.useBudget(limited)

                shouldThrow<SearchStopped> { fixture.translation.assertion(predicate, limited) }.reason shouldBe
                    TerminationReason.RESOURCE_LIMIT

                fixture.circuit.useBudget(fixture.budget)
                val literal = fixture.translation.assertion(predicate, fixture.budget)
                fixture.translation.realAtoms.size shouldBe 0
                for (value in 0..1) {
                    val fixed = fixture.translation.assertion(IntEquality(x, IntLiteral(value)), fixture.budget)
                    val expected = if (value == 0) SatStatus.SATISFIABLE else SatStatus.UNSATISFIABLE
                    fixture.solver.solve(intArrayOf(fixed, literal)).status shouldBe expected
                }
            }
        }

        test("predicates with an actual real variable retain an exact real theory atom") {
            val fixture = TranslationFixture()
            with(ConstraintScope.empty()) {
                val x = intVar("x", 0..3)
                val r = realVar("r")
                fixture.declare(this)

                fixture.translation.assertion(RealLessOrEqual(r, IntToReal(x)), fixture.budget)

                fixture.translation.realAtoms.size shouldBe 1
            }
        }
    })
