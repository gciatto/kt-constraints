package io.github.gciatto.ktconstraints.solve.auto

import io.github.gciatto.ktconstraints.core.Rational
import io.github.gciatto.ktconstraints.core.real.RealLiteral
import io.github.gciatto.ktconstraints.core.real.relation.RealLessThan
import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.solve.result.SolveStatus
import io.github.gciatto.ktconstraints.solve.spi.withSolver
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/** On Node the bundled routes are loaded asynchronously; pure Kotlin answers first and Z3 Wasm is registered after it. */
class AutoBackendJsSpec :
    FunSpec({
        test("defaults/js-tries-kotlin-before-z3-wasm") {
            val reals = ConstraintModel.of { add(RealLessThan(RealLiteral(Rational.of(1, 2)), realVar("r"))) }
            AutoBackend.load().use { auto ->
                auto.registry
                    .require("z3/wasm")
                    .descriptor.id shouldBe "z3/wasm"
                val result = auto.withSolver { it.solve(reals) }
                result.status shouldBe SolveStatus.FEASIBLE
                result.descriptor.id shouldBe "kotlin/direct"
            }
        }
    })
