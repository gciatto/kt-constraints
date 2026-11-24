package io.github.gciatto.ktconstraints.solve.spi

import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.solve.ConstraintSolver
import io.github.gciatto.ktconstraints.solve.SolveOptions
import io.github.gciatto.ktconstraints.solve.SolverDescriptor
import io.github.gciatto.ktconstraints.solve.assignment.Assignment
import io.github.gciatto.ktconstraints.solve.result.SolveResult
import io.github.gciatto.ktconstraints.solve.result.SolveStatus
import io.github.gciatto.ktconstraints.solve.spi.support.SupportReport
import io.github.gciatto.ktconstraints.solve.stream.SolutionFound
import io.github.gciatto.ktconstraints.solve.stream.SolveCompleted
import io.github.gciatto.ktconstraints.solve.stream.forEachSolution
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import org.gciatto.kt.math.BigInteger

/** Scoped backend helpers close what they create, and stream iteration returns the terminal result. */
class BackendHelpersSpec :
    FunSpec({
        test("with-solver/closes-the-solver-on-failure") {
            val backend = SingleSolverBackend(RecordingSolver())
            shouldThrow<IllegalStateException> { backend.withSolver { error("boom") } }
            shouldThrow<IllegalStateException> { backend.solver.solutions(ConstraintModel.empty()) }
        }

        test("create-incremental-solver/rejects-and-closes-batch-solvers") {
            val backend = SingleSolverBackend(RecordingSolver())
            shouldThrow<UnsupportedOperationException> { backend.createIncrementalSolver() }
            shouldThrow<IllegalStateException> { backend.solver.solutions(ConstraintModel.empty()) }
        }

        test("for-each-solution/visits-each-solution-and-returns-the-result") {
            val result = SolveResult(SolveStatus.FEASIBLE, Assignment.empty(), DESCRIPTOR)
            val events = listOf(SolutionFound(Assignment.empty()), SolutionFound(Assignment.empty(), BigInteger.ONE))
            val solver = RecordingSolver(events = events + SolveCompleted(result))
            val seen = mutableListOf<SolutionFound>()
            solver.solutions(ConstraintModel.empty()).use { stream ->
                stream.forEachSolution { seen += it } shouldBeSameInstanceAs result
            }
            seen shouldBe events
        }
    }) {
    private class SingleSolverBackend(
        val solver: ConstraintSolver,
    ) : SolverBackend {
        override val descriptor: SolverDescriptor = DESCRIPTOR

        override fun availability(): BackendAvailability = BackendAvailability(true)

        override fun support(
            model: ConstraintModel,
            options: SolveOptions,
        ): SupportReport = SupportReport()

        override fun createSolver(): ConstraintSolver = solver
    }

    private companion object {
        val DESCRIPTOR = SolverDescriptor("fake", "stream", emptySet())
    }
}
