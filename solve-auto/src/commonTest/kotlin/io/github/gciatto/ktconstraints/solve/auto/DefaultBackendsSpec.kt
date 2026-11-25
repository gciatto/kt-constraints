package io.github.gciatto.ktconstraints.solve.auto

import io.github.gciatto.ktconstraints.core.Rational
import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.solve.result.SolveStatus
import io.github.gciatto.ktconstraints.solve.spi.withSolver
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
    })
