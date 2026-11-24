package io.github.gciatto.ktconstraints.solve.kotlin.compiler

import io.github.gciatto.ktconstraints.core.Rational
import io.github.gciatto.ktconstraints.core.domain.IntDomain
import io.github.gciatto.ktconstraints.core.integer.IntLiteral
import io.github.gciatto.ktconstraints.core.integer.IntToReal
import io.github.gciatto.ktconstraints.core.integer.arithmetic.IntProduct
import io.github.gciatto.ktconstraints.core.integer.relation.IntEquality
import io.github.gciatto.ktconstraints.model.ConstraintScope
import io.github.gciatto.ktconstraints.solve.kotlin.compiler.linear.LinearForm
import io.github.gciatto.ktconstraints.solve.kotlin.search.SearchBudget
import io.github.gciatto.ktconstraints.solve.kotlin.search.SearchStopped
import io.github.gciatto.ktconstraints.solve.result.TerminationReason
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.gciatto.kt.math.BigInteger

class IntegerEmbeddingSpec :
    FunSpec({
        test("integer-to-real combines repeated complemented literals exactly") {
            val fixture = TranslationFixture()
            with(ConstraintScope.empty()) {
                val x = intVar("x", -1..0)
                fixture.declare(this)
                val expression = IntToReal(IntProduct(x, IntLiteral(3)))
                val form = fixture.translation.translate(expression, fixture.budget).value as LinearForm
                form.realCoefficients shouldBe emptyMap()
                form.bitCoefficients.size shouldBe 1
                for (value in -1L..0L) {
                    val equality = fixture.translation.assertion(IntEquality(x, IntLiteral(value)), fixture.budget)
                    val assignment = requireNotNull(fixture.solver.solve(intArrayOf(equality)).values)
                    form.constantUnder(assignment) shouldBe Rational.of(value * 3)
                }
            }
        }

        test("integer-to-real preserves large exact origins and domain holes") {
            val fixture = TranslationFixture()
            with(ConstraintScope.empty()) {
                val origin = -(BigInteger.ONE shl 200)
                val domain = IntDomain.of(origin, origin + 1).union(IntDomain.of(origin + 3, origin + 3))
                val x = intVar("x", domain)
                fixture.declare(this)
                val form = fixture.translation.translate(IntToReal(x), fixture.budget).value as LinearForm
                for (offset in listOf(0L, 1L, 3L)) {
                    val value = origin + offset
                    val equality = fixture.translation.assertion(IntEquality(x, IntLiteral(value)), fixture.budget)
                    val assignment = requireNotNull(fixture.solver.solve(intArrayOf(equality)).values)
                    form.constantUnder(assignment) shouldBe Rational.of(value)
                }
            }
        }

        test("wide integer-to-real embedding observes the work budget and can be retried") {
            val fixture = TranslationFixture()
            with(ConstraintScope.empty()) {
                val maximum = (BigInteger.ONE shl 1024) - BigInteger.ONE
                val x = intVar("x", IntDomain.of(BigInteger.ZERO, maximum))
                fixture.declare(this)
                fixture.translation.translate(x, fixture.budget)
                val expression = IntToReal(x)
                val limited = SearchBudget(null, 4) { false }
                fixture.circuit.useBudget(limited)

                val stopped = shouldThrow<SearchStopped> { fixture.translation.translate(expression, limited) }
                stopped.reason shouldBe TerminationReason.RESOURCE_LIMIT
                limited.work shouldBe 4L

                fixture.circuit.useBudget(fixture.budget)
                val form = fixture.translation.translate(expression, fixture.budget).value as LinearForm
                form.realCoefficients shouldBe emptyMap()
                form.constant shouldBe Rational.ZERO
                form.bitCoefficients.size shouldBe 1024
                form.bitCoefficients.values.fold(Rational.ZERO) { sum, coefficient -> sum + coefficient } shouldBe Rational.of(maximum)
            }
        }
    })
