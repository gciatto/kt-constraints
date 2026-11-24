package io.github.gciatto.ktconstraints.solve.auto

import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.solve.result.SolveStatus
import io.github.gciatto.ktconstraints.solve.spi.withSolver
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking

/** The blocking loader works on every platform that can block: the JVM and Kotlin/Native. */
class AutoBackendsSpec :
    FunSpec({
        test("blocking/load-auto-backend-solves-on-the-calling-thread") {
            val model = ConstraintModel.of { intVar("x", 0..9) }
            val status =
                loadAutoBackend().use { auto ->
                    auto.registry
                        .require("kotlin/direct")
                        .descriptor.id shouldBe "kotlin/direct"
                    auto.withSolver { solver -> runBlocking { solver.solve(model).status } }
                }
            status shouldBe SolveStatus.FEASIBLE
        }
    })
