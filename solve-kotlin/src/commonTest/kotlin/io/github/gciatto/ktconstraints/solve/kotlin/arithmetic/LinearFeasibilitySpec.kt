package io.github.gciatto.ktconstraints.solve.kotlin.arithmetic

import io.github.gciatto.ktconstraints.core.Rational
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CancellationException
import kotlin.random.Random

/** Exact witnesses and contradictions are checked independently of elimination and reconstruction. */
class LinearFeasibilitySpec :
    FunSpec({
        test("linear/empty-system-has-a-complete-zero-witness") {
            LinearFeasibility().solve(3, emptyList()).values shouldBe List(3) { Rational.ZERO }
            LinearFeasibility().solve(0, emptyList()).values shouldBe emptyList()
        }

        test("linear/constant-rows-retain-strict-semantics") {
            val engine = LinearFeasibility()
            engine.solve(0, listOf(LinearInequality(emptyMap(), Rational.ZERO))).values shouldBe emptyList()
            engine.solve(0, listOf(LinearInequality(emptyMap(), Rational.ONE, strict = true))).values shouldBe emptyList()
            engine.solve(0, listOf(LinearInequality(emptyMap(), Rational.ZERO, strict = true))).values shouldBe null
            engine.solve(0, listOf(LinearInequality(emptyMap(), -Rational.ONE))).values shouldBe null
        }

        test("linear/closed-bounds-can-fix-a-variable") {
            val rows =
                listOf(
                    LinearInequality(mapOf(0 to Rational.of(3)), Rational.of(2)),
                    LinearInequality(mapOf(0 to Rational.of(-3)), Rational.of(-2)),
                )
            LinearFeasibility().solve(1, rows).values shouldBe listOf(Rational.of(2, 3))
        }

        test("linear/strict-and-closed-bounds-differ-at-a-shared-endpoint") {
            val upper = LinearInequality(mapOf(0 to Rational.ONE), Rational.of(2), strict = true)
            val lower = LinearInequality(mapOf(0 to -Rational.ONE), Rational.of(-2))
            LinearFeasibility().solve(1, listOf(upper, lower)).values shouldBe null
        }

        test("linear/strict-bounds-have-rational-witnesses-without-a-floating-epsilon") {
            val lower = Rational.parse("123456789012345678901234567890/7")
            val upper = lower + Rational.parse("1/100000000000000000000000000000000000000000000000000")
            val rows =
                listOf(
                    LinearInequality(mapOf(0 to -Rational.ONE), -lower, strict = true),
                    LinearInequality(mapOf(0 to Rational.ONE), upper, strict = true),
                )
            val values = requireNotNull(LinearFeasibility().solve(1, rows).values)
            (values[0] > lower && values[0] < upper) shouldBe true
        }

        test("linear/unbounded-directions-still-produce-concrete-witnesses") {
            val rows =
                listOf(
                    LinearInequality(mapOf(0 to Rational.ONE), Rational.of(-19), strict = true),
                    LinearInequality(mapOf(1 to -Rational.ONE), Rational.of(-27), strict = true),
                )
            val values = requireNotNull(LinearFeasibility().solve(3, rows).values)
            rows.forEach { it.accepts(values) shouldBe true }
            values[2] shouldBe Rational.ZERO
        }

        test("linear/coupled-equalities-reconstruct-all-variables") {
            val rows =
                listOf(
                    LinearInequality(mapOf(0 to Rational.ONE, 1 to Rational.ONE), Rational.of(5)),
                    LinearInequality(mapOf(0 to -Rational.ONE, 1 to -Rational.ONE), Rational.of(-5)),
                    LinearInequality(mapOf(0 to Rational.ONE, 1 to -Rational.ONE), Rational.ONE),
                    LinearInequality(mapOf(0 to -Rational.ONE, 1 to Rational.ONE), -Rational.ONE),
                )
            LinearFeasibility().solve(2, rows).values shouldBe listOf(Rational.of(3), Rational.of(2))
        }

        test("linear/inconsistent-cycle-is-proved-without-finite-variable-bounds") {
            val rows =
                listOf(
                    LinearInequality(mapOf(0 to Rational.ONE, 1 to -Rational.ONE), -Rational.ONE),
                    LinearInequality(mapOf(1 to Rational.ONE, 2 to -Rational.ONE), -Rational.ONE),
                    LinearInequality(mapOf(2 to Rational.ONE, 0 to -Rational.ONE), -Rational.ONE),
                )
            LinearFeasibility().solve(3, rows).values shouldBe null
        }

        test("linear/strictness-survives-elimination-across-multiple-variables") {
            val rows =
                listOf(
                    LinearInequality(mapOf(0 to Rational.ONE, 1 to -Rational.ONE), Rational.ZERO, strict = true),
                    LinearInequality(mapOf(1 to Rational.ONE, 2 to -Rational.ONE), Rational.ZERO),
                    LinearInequality(mapOf(2 to Rational.ONE, 0 to -Rational.ONE), Rational.ZERO),
                )
            LinearFeasibility().solve(3, rows).values shouldBe null
        }

        test("linear/proportional-rows-keep-the-tightest-bound-and-strictness") {
            val rows =
                listOf(
                    LinearInequality(mapOf(0 to Rational.of(2)), Rational.of(8)),
                    LinearInequality(mapOf(0 to Rational.of(3)), Rational.of(9), strict = true),
                    LinearInequality(mapOf(0 to -Rational.ONE), Rational.of(-3)),
                )
            LinearFeasibility().solve(1, rows).values shouldBe null
        }

        test("linear/generated-feasible-systems-return-verified-witnesses") {
            val random = Random(0x1A7)
            repeat(30) {
                val witness = List(3) { Rational.of(random.nextInt(-5, 6).toLong(), random.nextInt(1, 5).toLong()) }
                val rows =
                    List(8) {
                        val coefficients = (0..2).associateWith { Rational.of(random.nextInt(-3, 4).toLong()) }
                        val activity = coefficients.entries.fold(Rational.ZERO) { total, (variable, value) -> total + value * witness[variable] }
                        LinearInequality(coefficients, activity + Rational.of(random.nextInt(1, 5).toLong(), 7), strict = random.nextBoolean())
                    }
                val values = requireNotNull(LinearFeasibility().solve(3, rows).values)
                values.size shouldBe 3
                rows.forEach { row -> row.accepts(values) shouldBe true }
            }
        }

        test("linear/cancellation-propagates-without-an-infeasibility-result") {
            var checkpoints = 0
            shouldThrow<CancellationException> {
                LinearFeasibility().solve(2, emptyList()) {
                    checkpoints++
                    throw CancellationException("requested")
                }
            }
            checkpoints shouldBe 1
        }

        test("linear/rejects-a-projected-contradiction-before-expanding-all-pairs") {
            val rows =
                (1..20).flatMap { coefficient ->
                    listOf(
                        LinearInequality(mapOf(0 to Rational.ONE, 1 to Rational.of(coefficient.toLong())), Rational.ZERO),
                        LinearInequality(
                            mapOf(0 to -Rational.ONE, 1 to Rational.of(-coefficient.toLong())),
                            Rational.ZERO,
                            strict = coefficient == 1,
                        ),
                    )
                }
            var checkpoints = 0
            val result =
                LinearFeasibility().solve(2, rows) {
                    if (++checkpoints > 250) throw CancellationException("work budget exceeded")
                }
            result.values shouldBe null
        }

        test("linear/repeated-solves-do-not-retain-old-assertions") {
            val engine = LinearFeasibility()
            engine.solve(0, listOf(LinearInequality(emptyMap(), -Rational.ONE))).values shouldBe null
            engine.solve(1, emptyList()).values shouldBe listOf(Rational.ZERO)
        }

        test("linear/invalid-variable-universes-are-rejected") {
            shouldThrow<IllegalArgumentException> { LinearFeasibility().solve(-1, emptyList()) }
            shouldThrow<IllegalArgumentException> {
                LinearFeasibility().solve(1, listOf(LinearInequality(mapOf(1 to Rational.ONE), Rational.ZERO)))
            }
            shouldThrow<IllegalArgumentException> {
                LinearFeasibility().solve(1, listOf(LinearInequality(mapOf(-1 to Rational.ONE), Rational.ZERO)))
            }
        }
    })

private fun LinearInequality.accepts(values: List<Rational>): Boolean {
    val activity = coefficients.entries.fold(Rational.ZERO) { total, (variable, coefficient) -> total + coefficient * values[variable] }
    return if (strict) activity < upperBound else activity <= upperBound
}
