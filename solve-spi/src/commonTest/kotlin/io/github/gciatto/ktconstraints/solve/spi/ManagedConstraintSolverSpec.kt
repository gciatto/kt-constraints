package io.github.gciatto.ktconstraints.solve.spi

import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.solve.TerminationReason
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/** A solver owns all active streams, including a stream racing with close. */
class ManagedConstraintSolverSpec : FunSpec({
    test("solver/close-cancels-all-streams-once-and-rejects-new-work") {
        val solver = RecordingSolver()
        val first = solver.solutions(ConstraintModel.of {})
        val second = solver.solutions(ConstraintModel.of {})
        solver.close()
        solver.close()
        first.terminalResult?.terminationReason shouldBe TerminationReason.CANCELLED
        second.terminalResult?.terminationReason shouldBe TerminationReason.CANCELLED
        solver.created.map { it.releases } shouldBe listOf(1, 1)
        shouldThrow<IllegalStateException> { solver.solutions(ConstraintModel.of {}) }
    }

    test("solver/close-racing-with-stream-creation-does-not-leak") {
        lateinit var solver: RecordingSolver
        solver = RecordingSolver(onOpen = { solver.close() })
        shouldThrow<IllegalStateException> { solver.solutions(ConstraintModel.of {}) }
        solver.created.single().releases shouldBe 1
    }

    test("solver/release-failure-does-not-leak-the-other-streams") {
        val solver = RecordingSolver(releaseHook = { error("release failed") })
        solver.solutions(ConstraintModel.of {})
        solver.solutions(ConstraintModel.of {})
        shouldThrow<IllegalStateException> { solver.close() }
        solver.created.map { it.releases } shouldBe listOf(1, 1)
        solver.close()
    }
})
