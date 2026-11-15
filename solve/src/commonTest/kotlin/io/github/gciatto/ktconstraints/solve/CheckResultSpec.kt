package io.github.gciatto.ktconstraints.solve

import io.github.gciatto.ktconstraints.core.BoolLiteral
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withTests
import io.kotest.matchers.shouldBe

/** Incremental check results never claim satisfiability or unsatisfiability without evidence. */
class CheckResultSpec :
    FunSpec({
        test("check/satisfiable-requires-an-assignment") {
            val descriptor = SolverDescriptor("example", "direct", emptySet())
            shouldThrow<IllegalArgumentException> { CheckResult(CheckStatus.SATISFIABLE, null, descriptor) }
            CheckResult(CheckStatus.SATISFIABLE, Assignment.empty(), descriptor).status shouldBe CheckStatus.SATISFIABLE
        }

        withTests(nameFn = { "check/no-assignment-without-satisfiability/$it" }, listOf(CheckStatus.UNSATISFIABLE, CheckStatus.UNKNOWN)) {
            val descriptor = SolverDescriptor("example", "direct", emptySet())
            shouldThrow<IllegalArgumentException> { CheckResult(it, Assignment.empty(), descriptor) }
        }

        withTests(nameFn = { "check/proof-requires-completion/$it" }, listOf(CheckStatus.SATISFIABLE, CheckStatus.UNSATISFIABLE)) {
            val descriptor = SolverDescriptor("example", "direct", emptySet())
            val assignment = if (it == CheckStatus.SATISFIABLE) Assignment.empty() else null
            shouldThrow<IllegalArgumentException> { CheckResult(it, assignment, descriptor, TerminationReason.TIMEOUT) }
        }

        test("check/only-unsatisfiable-results-carry-a-detached-core") {
            val descriptor = SolverDescriptor("example", "direct", emptySet())
            val core = mutableListOf(BoolLiteral(false))
            val result = CheckResult(CheckStatus.UNSATISFIABLE, null, descriptor, unsatCore = core)
            core.clear()
            result.unsatCore shouldBe listOf(BoolLiteral(false))
            shouldThrow<IllegalArgumentException> {
                CheckResult(CheckStatus.UNKNOWN, null, descriptor, TerminationReason.TIMEOUT, unsatCore = listOf(BoolLiteral(false)))
            }
        }
    })
