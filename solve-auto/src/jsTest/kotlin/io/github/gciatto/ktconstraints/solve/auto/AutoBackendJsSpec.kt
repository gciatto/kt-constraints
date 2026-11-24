package io.github.gciatto.ktconstraints.solve.auto

import io.github.gciatto.ktconstraints.core.Rational
import io.github.gciatto.ktconstraints.core.real.RealLiteral
import io.github.gciatto.ktconstraints.core.real.relation.RealLessThan
import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.solve.result.SolveStatus
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/** On Node the bundled routes are loaded asynchronously and Z3 Wasm answers SMT models. */
class AutoBackendJsSpec :
    FunSpec({
        test("defaults/js-routes-reals-to-z3-wasm") {
            val reals = ConstraintModel.of { add(RealLessThan(RealLiteral(Rational.of(1, 2)), realVar("r"))) }
            AutoBackend.load().use { loaded ->
                loaded.registry
                    .rank(reals)
                    .best.backend.descriptor.id shouldBe "z3/wasm"
            }
            AutoBackend.load().use { auto -> auto.create().use { it.solve(reals) } }.status shouldBe SolveStatus.FEASIBLE
        }
    })
