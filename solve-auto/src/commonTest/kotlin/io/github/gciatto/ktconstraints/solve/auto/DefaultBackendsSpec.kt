package io.github.gciatto.ktconstraints.solve.auto

import io.github.gciatto.ktconstraints.core.Rational
import io.github.gciatto.ktconstraints.core.integer.IntToReal
import io.github.gciatto.ktconstraints.core.integer.arithmetic.IntProduct
import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.solve.result.SolveStatus
import io.github.gciatto.ktconstraints.solve.spi.exception.UnsupportedModelException
import io.github.gciatto.ktconstraints.solve.spi.withSolver
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/** Every platform registers the pure-Kotlin route, and an exact route for real objectives. */
class DefaultBackendsSpec :
    FunSpec({
        test("defaults/kotlin-is-registered-on-every-platform") {
            AutoBackend.load().use { auto ->
                auto.registry
                    .require("kotlin/direct")
                    .descriptor.id shouldBe "kotlin/direct"
            }
        }

        test("defaults/real-objectives-reach-an-exact-route") {
            val model =
                ConstraintModel.of {
                    val x = realVar("x")
                    add(x le Rational.of(1, 2))
                    maximize(x)
                }
            val result = AutoBackend.load().use { auto -> auto.withSolver { it.solve(model) } }
            result.status shouldBe SolveStatus.OPTIMAL
            result.realObjectiveValue shouldBe Rational.of(1, 2)
        }

        test("defaults/quadratic-real-objectives-are-diagnosed-before-any-route-runs") {
            val model =
                ConstraintModel.of {
                    val x = intVar("x", 0..3)
                    val r = realVar("r")
                    add(r le 1)
                    maximize(IntToReal(IntProduct(x, x)) + r)
                }
            val failure = AutoBackend.load().use { auto -> shouldThrow<UnsupportedModelException> { auto.withSolver { it.solve(model) } } }
            val reasons = failure.report.unsupported
            reasons.first() shouldBe "model: SMT_REAL"
            reasons.any { "NONLINEAR_REAL_OBJECTIVES, which no registered backend offers" in it && "quadratic" in it } shouldBe true
            reasons.filter { "rejected" in it }.filterNot { "Missing capability: NONLINEAR_REAL_OBJECTIVES" in it } shouldBe emptyList()
        }
    })
