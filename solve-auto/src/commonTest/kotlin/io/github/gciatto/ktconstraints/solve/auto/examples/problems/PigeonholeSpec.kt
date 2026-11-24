package io.github.gciatto.ktconstraints.solve.auto.examples.problems

import io.github.gciatto.ktconstraints.solve.incremental.CheckStatus
import io.github.gciatto.ktconstraints.solve.result.SolveStatus
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

/** Executable Pigeonhole cookbook example. */
class PigeonholeSpec :
    FunSpec({
        test("prove three pigeons cannot occupy two distinct holes") {
            val result = solvePigeonhole()
            result.status shouldBe SolveStatus.INFEASIBLE
            result.assignment shouldBe null
        }
        test("explain infeasibility through temporary pairwise assumptions") {
            val conflict = explainPigeonhole()
            conflict.core shouldHaveSize 3
            conflict.rules shouldContainAll conflict.core
            conflict.coreStatus shouldBe CheckStatus.UNSATISFIABLE
            conflict.withoutOneRule shouldBe List(3) { CheckStatus.SATISFIABLE }
            conflict.withoutAssumptions shouldBe CheckStatus.SATISFIABLE
        }
    })
