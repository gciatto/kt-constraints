package io.github.gciatto.ktconstraints.solve.auto

import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.solve.spi.withSolver
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

private val MINIZINC_ROUTES = listOf("gecode", "chuffed", "cp-sat", "highs", "cbc").map { "$it/minizinc" }

/** Native bundles pure Kotlin first, then the Z3 C API route loaded at run time, then MiniZinc when configured. */
class AutoBackendNativeSpec :
    FunSpec({
        test("defaults/native-registers-z3") {
            AutoBackend.load().use {
                it.registry
                    .require("z3/native")
                    .descriptor.id shouldBe "z3/native"
            }
        }

        test("defaults/native-registers-minizinc-engines-when-configured") {
            val names = AutoBackend.load().use { auto -> MINIZINC_ROUTES.filter { runCatching { auto.registry.require(it) }.isSuccess } }
            names shouldBe if (benchmarkEnvironment("MINIZINC_EXECUTABLE") == null) emptyList() else MINIZINC_ROUTES
        }

        test("defaults/native-tries-kotlin-before-z3") {
            val model = ConstraintModel.of { intVar("x", 0..9) }
            AutoBackend
                .load()
                .use { auto -> auto.withSolver { it.solve(model) } }
                .descriptor.id shouldBe "kotlin/direct"
        }
    })
