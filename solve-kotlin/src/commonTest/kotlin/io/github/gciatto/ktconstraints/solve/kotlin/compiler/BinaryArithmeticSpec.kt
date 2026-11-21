package io.github.gciatto.ktconstraints.solve.kotlin.compiler

import io.github.gciatto.ktconstraints.core.IntDomain
import io.github.gciatto.ktconstraints.solve.TerminationReason
import io.github.gciatto.ktconstraints.solve.kotlin.KotlinConfiguration
import io.github.gciatto.ktconstraints.solve.kotlin.SearchBudget
import io.github.gciatto.ktconstraints.solve.kotlin.SearchStopped
import io.github.gciatto.ktconstraints.solve.kotlin.sat.SatSolver
import io.github.gciatto.ktconstraints.solve.kotlin.sat.SatStatus
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CancellationException
import org.gciatto.kt.math.BigInteger

/** Machine arithmetic is used only as an independent oracle for exhaustively bounded examples. */
class BinaryArithmeticSpec :
    FunSpec({
        test("integer-circuit/constants-preserve-sign-and-arbitrary-precision") {
            val fixture = BinaryFixture()
            val values =
                (-10L..10L).map(BigInteger::of) +
                    listOf(BigInteger.ONE shl 200, -(BigInteger.ONE shl 200), -(BigInteger.ONE shl 200) - 1)
            val encoded = values.map { fixture.arithmetic.integer(it) }
            val assignment = requireNotNull(fixture.solver.solve().values)
            encoded.map { fixture.arithmetic.decodeInteger(it, assignment) } shouldBe values
        }

        test("integer-circuit/exhaustive-arithmetic-does-not-wrap") {
            val fixture = BinaryFixture()
            val arithmetic = fixture.arithmetic
            val x = arithmetic.variable(IntDomain.of(-3, 3))
            val y = arithmetic.variable(IntDomain.of(-2, 4))
            val expressions =
                listOf(arithmetic.add(x, y), arithmetic.subtract(x, y), arithmetic.multiply(x, y), arithmetic.negate(x))
            for (left in -3L..3L) {
                for (right in -2L..4L) {
                    val assumptions =
                        intArrayOf(
                            arithmetic.equal(x, arithmetic.integer(BigInteger.of(left))),
                            arithmetic.equal(y, arithmetic.integer(BigInteger.of(right))),
                        )
                    val expected = listOf(left + right, left - right, left * right, -left).map(BigInteger::of)
                    val assignment = requireNotNull(fixture.solver.solve(assumptions).values)
                    expressions.map { arithmetic.decodeInteger(it, assignment) } shouldBe expected
                    for (index in expressions.indices) {
                        val equality = arithmetic.equal(expressions[index], arithmetic.integer(expected[index]))
                        fixture.solver.solve(assumptions + -equality).status shouldBe SatStatus.UNSATISFIABLE
                    }
                }
            }
            expressions[0].min shouldBe BigInteger.of(-5)
            expressions[0].max shouldBe BigInteger.of(7)
            expressions[2].min shouldBe BigInteger.of(-12)
            expressions[2].max shouldBe BigInteger.of(12)
        }

        test("integer-circuit/one-bit-negative-variable-negates-with-a-wider-result") {
            val fixture = BinaryFixture()
            val x = fixture.arithmetic.variable(IntDomain.of(-1, 0))
            val negative = fixture.arithmetic.negate(x)
            val equality = fixture.arithmetic.equal(x, fixture.arithmetic.integer(-BigInteger.ONE))
            val assignment = requireNotNull(fixture.solver.solve(intArrayOf(equality)).values)
            fixture.arithmetic.decodeInteger(negative, assignment) shouldBe BigInteger.ONE
        }

        test("integer-circuit/sparse-domain-holes-are-enforced") {
            val fixture = BinaryFixture()
            val domain = IntDomain.of(-5, -3).union(IntDomain.of(5, 6))
            val x = fixture.arithmetic.variable(domain)
            for (candidate in -6L..7L) {
                val equality = fixture.arithmetic.equal(x, fixture.arithmetic.integer(BigInteger.of(candidate)))
                val result = fixture.solver.solve(intArrayOf(equality))
                result.status shouldBe if (candidate in domain) SatStatus.SATISFIABLE else SatStatus.UNSATISFIABLE
                result.values?.let { fixture.arithmetic.decodeInteger(x, it) shouldBe BigInteger.of(candidate) }
            }
        }

        test("integer-circuit/huge-base-small-domain-uses-few-decision-bits") {
            val fixture = BinaryFixture()
            val base = BigInteger.ONE shl 200
            val x = fixture.arithmetic.variable(IntDomain.of(base, base + 3))
            (fixture.circuit.variables < 30) shouldBe true
            for (offset in 0L..3L) {
                val expected = base + offset
                val equality = fixture.arithmetic.equal(x, fixture.arithmetic.integer(expected))
                val assignment = requireNotNull(fixture.solver.solve(intArrayOf(equality)).values)
                fixture.arithmetic.decodeInteger(x, assignment) shouldBe expected
            }
        }

        test("integer-circuit/empty-domain-is-a-contradiction") {
            val fixture = BinaryFixture()
            fixture.arithmetic.variable(IntDomain.empty())
            fixture.solver.solve().status shouldBe SatStatus.UNSATISFIABLE
        }

        test("integer-circuit/widening-respects-the-configured-resource-limit-before-allocation") {
            val solver = SatSolver()
            val circuit = Circuit(solver, KotlinConfiguration(maxBitWidth = 3), SearchBudget(null, null) { false })
            val arithmetic = BinaryArithmetic(circuit)
            val x = arithmetic.variable(IntDomain.of(-2, 2))
            val allocated = circuit.variables
            val failure = shouldThrow<SearchStopped> { arithmetic.multiply(x, x) }
            failure.reason shouldBe TerminationReason.RESOURCE_LIMIT
            circuit.variables shouldBe allocated
        }

        test("integer-circuit/multiplication-observes-cooperative-cancellation") {
            val fixture = BinaryFixture()
            val magnitude = BigInteger.ONE shl 63
            val x = fixture.arithmetic.variable(IntDomain.of(-magnitude, magnitude - BigInteger.ONE))
            var checkpoints = 0
            val arithmetic =
                BinaryArithmetic(fixture.circuit) {
                    if (++checkpoints == 2) throw CancellationException("requested")
                }
            shouldThrow<CancellationException> { arithmetic.multiply(x, x) }
            checkpoints shouldBe 2
        }

        test("integer-circuit/comparison-and-choice-respect-signed-values") {
            val fixture = BinaryFixture()
            val arithmetic = fixture.arithmetic
            val x = arithmetic.variable(IntDomain.of(-2, 2))
            val y = arithmetic.variable(IntDomain.of(-3, 3))
            val condition = fixture.circuit.variable()
            val selected = arithmetic.choose(condition, x, y)
            val less = arithmetic.less(x, y)
            for (left in -2L..2L) {
                for (right in -3L..3L) {
                    for (takeLeft in listOf(false, true)) {
                        val assumptions =
                            intArrayOf(
                                arithmetic.equal(x, arithmetic.integer(BigInteger.of(left))),
                                arithmetic.equal(y, arithmetic.integer(BigInteger.of(right))),
                                if (takeLeft) condition else -condition,
                            )
                        val assignment = requireNotNull(fixture.solver.solve(assumptions).values)
                        arithmetic.decodeInteger(selected, assignment) shouldBe BigInteger.of(if (takeLeft) left else right)
                        fixture.circuit.value(less, assignment) shouldBe (left < right)
                        fixture.solver.solve(assumptions + if (left < right) -less else less).status shouldBe SatStatus.UNSATISFIABLE
                    }
                }
            }
        }
    })
