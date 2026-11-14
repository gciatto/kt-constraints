package io.github.gciatto.ktconstraints.solve

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withTests
import io.kotest.matchers.shouldBe
import org.gciatto.kt.math.BigInteger

/** Proof of feasibility/optimality is never inferred from termination alone. */
class SolveResultSpec :
    FunSpec({
        test("result/timeout-can-retain-a-feasible-incumbent-with-an-exact-bound") {
            val descriptor = SolverDescriptor("example", "direct", emptySet())
            val result = SolveResult(
                SolveStatus.FEASIBLE,
                Assignment.empty(),
                descriptor,
                TerminationReason.TIMEOUT,
                objectiveValue = BigInteger.of("9223372036854775808"),
                bestBound = BigInteger.ZERO,
            )
            result.status shouldBe SolveStatus.FEASIBLE
            result.terminationReason shouldBe TerminationReason.TIMEOUT
            result.objectiveValue.toString() shouldBe "9223372036854775808"
            result.bestBound shouldBe BigInteger.ZERO
        }

        withTests(nameFn = { "result/proof-requires-completion/$it" },
            listOf(SolveStatus.OPTIMAL, SolveStatus.INFEASIBLE, SolveStatus.UNBOUNDED, SolveStatus.INFEASIBLE_OR_UNBOUNDED),
        ) { status ->
            val descriptor = SolverDescriptor("example", "direct", emptySet())
            val assignment = if (status == SolveStatus.OPTIMAL) Assignment.empty() else null
            shouldThrow<IllegalArgumentException> { SolveResult(status, assignment, descriptor, TerminationReason.TIMEOUT) }
        }

        test("result/feasible-status-requires-an-assignment") {
            val descriptor = SolverDescriptor("example", "direct", emptySet())
            shouldThrow<IllegalArgumentException> { SolveResult(SolveStatus.FEASIBLE, null, descriptor) }
            shouldThrow<IllegalArgumentException> { SolveResult(SolveStatus.OPTIMAL, null, descriptor) }
            shouldThrow<IllegalArgumentException> {
                SolveResult(SolveStatus.UNKNOWN, Assignment.empty(), descriptor)
            }
            shouldThrow<IllegalArgumentException> {
                SolveResult(SolveStatus.UNKNOWN, null, descriptor, objectiveValue = BigInteger.ONE)
            }
            SolveResult(SolveStatus.INFEASIBLE, null, descriptor).terminationReason shouldBe TerminationReason.COMPLETED
        }

        test("result/optimal-status-requires-an-objective-value") {
            val descriptor = SolverDescriptor("example", "direct", emptySet())
            shouldThrow<IllegalArgumentException> {
                SolveResult(SolveStatus.OPTIMAL, Assignment.empty(), descriptor)
            }
            SolveResult(
                SolveStatus.OPTIMAL,
                Assignment.empty(),
                descriptor,
                objectiveValue = BigInteger.ONE,
                bestBound = BigInteger.ONE,
            ).status shouldBe SolveStatus.OPTIMAL
        }

        test("result/optimal-objective-and-provided-bound-must-agree") {
            val descriptor = SolverDescriptor("example", "direct", emptySet())
            shouldThrow<IllegalArgumentException> {
                SolveResult(
                    SolveStatus.OPTIMAL,
                    Assignment.empty(),
                    descriptor,
                    objectiveValue = BigInteger.ONE,
                    bestBound = BigInteger.ZERO,
                )
            }
        }
    })
