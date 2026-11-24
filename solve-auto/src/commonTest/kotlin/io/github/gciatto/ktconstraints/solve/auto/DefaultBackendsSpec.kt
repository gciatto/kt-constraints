package io.github.gciatto.ktconstraints.solve.auto

import io.github.gciatto.ktconstraints.core.integer.IntVar
import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.solve.assignment.Assignment
import io.github.gciatto.ktconstraints.solve.kotlin.KotlinBackend
import io.github.gciatto.ktconstraints.solve.result.SolveResult
import io.github.gciatto.ktconstraints.solve.result.SolveStatus
import io.github.gciatto.ktconstraints.solve.spi.ranking.RankingPolicy
import io.github.gciatto.ktconstraints.solve.spi.withSolver
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.gciatto.kt.math.BigInteger

/** Every platform registers the pure-Kotlin route and ranks it after all other eligible routes. */
class DefaultBackendsSpec :
    FunSpec({
        val model = ConstraintModel.of { intVar("x", 0..9) }

        suspend fun solveWith(vararg others: ScriptedBackend): SolveResult =
            // Kotlin is registered first, so only the platform policy can push it behind the others.
            AutoBackend(listOf(KotlinBackend(), *others), policy = DefaultBackends.policy(RankingPolicy.DEFAULT)).use { auto ->
                auto.withSolver { it.solve(model) }
            }

        test("defaults/kotlin-is-registered-on-every-platform") {
            AutoBackend.load().use { auto ->
                auto.registry
                    .require("kotlin/direct")
                    .descriptor.id shouldBe "kotlin/direct"
            }
        }

        test("defaults/another-available-route-answers-before-kotlin") {
            val log = AutoLog()
            val answering =
                ScriptedBackend("other", 0, log) { model, descriptor, _ ->
                    val x = model.variables.single() as IntVar
                    SolveResult(SolveStatus.FEASIBLE, Assignment.of(integers = mapOf(x to BigInteger.ONE)), descriptor)
                }
            solveWith(answering).descriptor.id shouldBe "other/direct"
            log.created shouldBe listOf("other")
        }

        test("defaults/kotlin-answers-when-no-other-route-is-available") {
            val log = AutoLog()
            val missing =
                ScriptedBackend(
                    "other",
                    0,
                    log,
                    available = false,
                ) { _, descriptor, _ -> SolveResult(SolveStatus.UNKNOWN, null, descriptor) }
            solveWith(missing).descriptor.id shouldBe "kotlin/direct"
        }
    })
