package io.github.gciatto.ktconstraints.solve.spi

import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.solve.SolverDescriptor
import io.github.gciatto.ktconstraints.solve.assignment.Assignment
import io.github.gciatto.ktconstraints.solve.result.SolveResult
import io.github.gciatto.ktconstraints.solve.result.SolveStatus
import io.github.gciatto.ktconstraints.solve.result.TerminationReason
import io.github.gciatto.ktconstraints.solve.stream.SolutionFound
import io.github.gciatto.ktconstraints.solve.stream.SolveCompleted
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import org.gciatto.kt.math.BigInteger

/** A solver owns all active streams, including a stream racing with close. */
class ManagedConstraintSolverSpec :
    FunSpec({
        test("solver/close-cancels-all-streams-once-and-rejects-new-work") {
            val solver = RecordingSolver()
            val first = solver.solutions(ConstraintModel.empty())
            val second = solver.solutions(ConstraintModel.empty())
            solver.close()
            solver.close()
            first.terminalResult?.terminationReason shouldBe TerminationReason.CANCELLED
            second.terminalResult?.terminationReason shouldBe TerminationReason.CANCELLED
            solver.created.map { it.releases } shouldBe listOf(1, 1)
            shouldThrow<IllegalStateException> { solver.solutions(ConstraintModel.empty()) }
        }

        test("solver/close-racing-with-stream-creation-does-not-leak") {
            lateinit var solver: RecordingSolver
            solver = RecordingSolver(onOpen = { solver.close() })
            shouldThrow<IllegalStateException> { solver.solutions(ConstraintModel.empty()) }
            solver.created.single().releases shouldBe 1
        }

        test("solver/release-failure-does-not-leak-the-other-streams") {
            val solver = RecordingSolver(releaseHook = { error("release failed") })
            solver.solutions(ConstraintModel.empty())
            solver.solutions(ConstraintModel.empty())
            shouldThrow<IllegalStateException> { solver.close() }
            solver.created.map { it.releases } shouldBe listOf(1, 1)
            solver.close()
        }

        test("solver/batch-satisfaction-stops-at-the-first-solution") {
            val first = Assignment.empty()
            val solver =
                RecordingSolver(events = listOf(SolutionFound(first), SolutionFound(Assignment.empty()), completion(SolveStatus.FEASIBLE)))
            val result = solver.solve(ConstraintModel.of { boolVar("b") })
            result.status shouldBe SolveStatus.FEASIBLE
            result.terminationReason shouldBe TerminationReason.COMPLETED
            result.assignment shouldBeSameInstanceAs first
            result.descriptor shouldBe solver.descriptor
            solver.created.single().reads shouldBe 1
            solver.created.single().releases shouldBe 1
        }

        test("solver/batch-satisfaction-without-solutions-reports-the-completion") {
            val solver = RecordingSolver(events = listOf(completion(SolveStatus.INFEASIBLE)))
            val result = solver.solve(ConstraintModel.of { boolVar("b") })
            result.status shouldBe SolveStatus.INFEASIBLE
            solver.created.single().releases shouldBe 1
        }

        test("solver/batch-optimization-reads-until-completion") {
            val best = Assignment.empty()
            val events =
                listOf(
                    SolutionFound(Assignment.empty(), BigInteger.ONE),
                    SolutionFound(best, BigInteger.TWO),
                    SolveCompleted(SolveResult(SolveStatus.OPTIMAL, best, DESCRIPTOR, objectiveValue = BigInteger.TWO)),
                )
            val solver = RecordingSolver(events = events)
            val result = solver.solve(ConstraintModel.of { maximize(intVar("x", 0..2)) })
            result.status shouldBe SolveStatus.OPTIMAL
            result.objectiveValue shouldBe BigInteger.TWO
            solver.created.single().reads shouldBe 3
        }
    })

private val DESCRIPTOR = SolverDescriptor("fake", "stream", emptySet())

private fun completion(status: SolveStatus) =
    SolveCompleted(
        SolveResult(
            status,
            if (status ==
                SolveStatus.FEASIBLE
            ) {
                Assignment.empty()
            } else {
                null
            },
            DESCRIPTOR,
        ),
    )
