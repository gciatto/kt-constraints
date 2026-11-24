package io.github.gciatto.ktconstraints.solve.auto.examples.problems

import io.github.gciatto.ktconstraints.solve.incremental.CheckStatus
import io.github.gciatto.ktconstraints.solve.result.SolveStatus
import io.kotest.core.spec.style.FunSpec
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
            val (conflictWithAssumptions, rechecked, recovery) = pigeonholeCore()
            val (conflict, separate) = conflictWithAssumptions
            val core = requireNotNull(conflict.unsatCore)
            conflict.status shouldBe CheckStatus.UNSATISFIABLE
            separate.containsAll(core) shouldBe true
            core.size shouldBe 3
            rechecked.status shouldBe CheckStatus.UNSATISFIABLE
            val (recovered, subsets) = recovery
            for (subset in subsets) {
                subset.status shouldBe CheckStatus.SATISFIABLE
            }
            recovered.status shouldBe CheckStatus.SATISFIABLE
        }
    })
