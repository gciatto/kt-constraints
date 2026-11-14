package io.github.gciatto.ktconstraints.solve.spi

import io.github.gciatto.ktconstraints.solve.Assignment
import io.github.gciatto.ktconstraints.solve.SolutionFound
import io.github.gciatto.ktconstraints.solve.SolveCompleted
import io.github.gciatto.ktconstraints.solve.SolveResult
import io.github.gciatto.ktconstraints.solve.SolveStatus
import io.github.gciatto.ktconstraints.solve.SolverDescriptor
import io.github.gciatto.ktconstraints.solve.TerminationReason
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import org.gciatto.kt.math.BigInteger

/** Shared stream lifecycle is exercised independently of any engine. */
class ManagedSolutionStreamSpec :
    FunSpec({
        test("stream/backend-deadline-retains-incumbent-with-timeout-reason") {
            val stream = RecordingStream(listOf(SolutionFound(Assignment.empty(), BigInteger.ONE)))
            stream.next()
            stream.timeout()
            stream.terminalResult?.terminationReason shouldBe TerminationReason.TIMEOUT
            stream.terminalResult?.status shouldBe SolveStatus.FEASIBLE
            stream.cancel()
            stream.terminalResult?.terminationReason shouldBe TerminationReason.TIMEOUT
        }
        test("stream/emits-one-completion-and-releases-on-exhaustion") {
            val result = SolveResult(SolveStatus.FEASIBLE, Assignment.empty(), SolverDescriptor("fake", "stream", emptySet()))
            val solution = SolutionFound(Assignment.empty())
            val stream = RecordingStream(listOf(solution, SolveCompleted(result)))
            stream.next() shouldBe solution
            stream.next() shouldBe SolveCompleted(result)
            stream.next() shouldBe null
            stream.close()
            stream.cancel()
            stream.terminalResult shouldBe result
            stream.releases shouldBe 1
        }

        test("stream/cancel-before-read-is-definitive-and-idempotent") {
            val stream = RecordingStream()
            stream.cancel()
            stream.close()
            stream.terminalResult?.status shouldBe SolveStatus.UNKNOWN
            stream.terminalResult?.terminationReason shouldBe TerminationReason.CANCELLED
            (stream.next() is SolveCompleted) shouldBe true
            stream.next() shouldBe null
            stream.reads shouldBe 0
            stream.releases shouldBe 1
        }

        test("stream/cancellation-retains-the-latest-incumbent-without-claiming-optimality") {
            val stream = RecordingStream(listOf(SolutionFound(Assignment.empty(), BigInteger.TWO)))
            stream.next()
            stream.cancel()
            stream.terminalResult?.status shouldBe SolveStatus.FEASIBLE
            stream.terminalResult?.objectiveValue shouldBe BigInteger.TWO
            stream.terminalResult?.terminationReason shouldBe TerminationReason.CANCELLED
        }

        test("stream/read-failure-releases-resources-and-remains-a-failure") {
            val stream = RecordingStream(failure = IllegalStateException("broken transport"))
            shouldThrow<IllegalStateException> { stream.next() }
            stream.terminalResult?.status shouldBe SolveStatus.UNKNOWN
            stream.terminalResult?.terminationReason shouldBe TerminationReason.SOLVER_FAILURE
            (stream.next() is SolveCompleted) shouldBe true
            stream.close()
            stream.releases shouldBe 1
        }

        test("stream/cancel-during-read-wins-over-a-late-solution") {
            val entered = CompletableDeferred<Unit>()
            val released = CompletableDeferred<Unit>()
            val stream =
                RecordingStream(listOf(SolutionFound(Assignment.empty())), readHook = {
                    entered.complete(Unit)
                    released.await()
                }, releaseHook = { released.complete(Unit) })
            coroutineScope {
                val event = async { stream.next() }
                entered.await()
                shouldThrow<IllegalStateException> { stream.next() }
                stream.cancel()
                (event.await() is SolveCompleted) shouldBe true
            }
            stream.terminalResult?.status shouldBe SolveStatus.UNKNOWN
            stream.releases shouldBe 1
        }

        test("stream/coroutine-cancellation-propagates-and-cleans-up") {
            val entered = CompletableDeferred<Unit>()
            val wait = CompletableDeferred<Unit>()
            val stream =
                RecordingStream(readHook = {
                    entered.complete(Unit)
                    wait.await()
                })
            coroutineScope {
                val reader = launch { stream.next() }
                entered.await()
                reader.cancelAndJoin()
            }
            stream.terminalResult?.terminationReason shouldBe TerminationReason.CANCELLED
            stream.releases shouldBe 1
        }

        test("stream/explicit-cancellation-exception-is-never-infeasibility") {
            val stream = RecordingStream(failure = CancellationException("cancelled"))
            shouldThrow<CancellationException> { stream.next() }
            stream.terminalResult?.status shouldBe SolveStatus.UNKNOWN
            stream.terminalResult?.terminationReason shouldBe TerminationReason.CANCELLED
        }

        test("stream/rejects-a-completion-from-another-route") {
            val result = SolveResult(SolveStatus.INFEASIBLE, null, SolverDescriptor("other", "route", emptySet()))
            val stream = RecordingStream(listOf(SolveCompleted(result)))
            shouldThrow<IllegalArgumentException> { stream.next() }
            stream.terminalResult?.terminationReason shouldBe TerminationReason.SOLVER_FAILURE
            stream.releases shouldBe 1
        }

        test("stream/detects-cancellation-even-when-the-producer-returns-without-suspending") {
            val stream =
                RecordingStream(listOf(SolutionFound(Assignment.empty())), readHook = {
                    currentCoroutineContext().cancel()
                })
            coroutineScope { launch { stream.next() }.join() }
            stream.terminalResult?.terminationReason shouldBe TerminationReason.CANCELLED
            stream.releases shouldBe 1
        }

        test("stream/cleanup-errors-do-not-hide-the-original-read-failure") {
            val stream = RecordingStream(failure = IllegalStateException("read failed"), releaseHook = { error("cleanup failed") })
            val failure = shouldThrow<IllegalStateException> { stream.next() }
            failure.message shouldBe "read failed"
            failure.suppressedExceptions.single().message shouldBe "cleanup failed"
            stream.releases shouldBe 1
        }
    })
