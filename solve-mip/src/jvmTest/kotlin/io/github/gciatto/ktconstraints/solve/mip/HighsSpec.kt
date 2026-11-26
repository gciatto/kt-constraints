package io.github.gciatto.ktconstraints.solve.mip

import io.github.gciatto.ktconstraints.solve.result.SolveStatus
import io.github.gciatto.ktconstraints.solve.result.TerminationReason
import io.github.gciatto.ktconstraints.solve.spi.withSolver
import io.github.gciatto.ktconstraints.test.solve.benchmark.LinearCatalogue
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlin.time.TimeSource

/** HiGHS through OR-Tools: shared MIP route tests plus the mandatory per-solve limit that bounds abandoned solves. */
class HighsSpec :
    FunSpec({
        include(mipRoute("highs/or-tools") { OrToolsMipBackend.highs(LIMIT_MILLIS) })

        test("highs/or-tools/requires-a-positive-solve-limit") {
            shouldThrow<IllegalArgumentException> { OrToolsMipBackend.highs(0) }
            OrToolsMipBackend.highs(LIMIT_MILLIS).solveLimitMillis shouldBe LIMIT_MILLIS
        }

        test("highs/or-tools/optimality-is-proven-without-a-gap") {
            // HiGHS' default 1e-4 relative gap once called 29572 optimal here; the optimum is 29574.
            val instance = LinearCatalogue.boundedKnapsack(36, 1)
            val result = OrToolsMipBackend.highs(LIMIT_MILLIS).withSolver { it.solve(instance.model) }
            result.status shouldBe SolveStatus.OPTIMAL
            result.objectiveValue shouldBe instance.expectedObjective
        }

        test("highs/or-tools/backend-limit-is-a-resource-limit") {
            OrToolsMipBackend.highs(300).withSolver { solver ->
                val started = TimeSource.Monotonic.markNow()
                val result = solver.solve(marketSplit())
                started.elapsedNow().inWholeMilliseconds shouldBeLessThan 10_000L
                result.status shouldBe SolveStatus.UNKNOWN
                result.terminationReason shouldBe TerminationReason.RESOURCE_LIMIT
            }
        }

        test("highs/or-tools/close-returns-at-once-and-the-solve-ends-at-its-limit") {
            OrToolsMipBackend.highs(2_000).withSolver { solver ->
                val stream = solver.solutions(marketSplit())
                coroutineScope {
                    val reading = async { runCatching { stream.next() } }
                    delay(100)
                    val closing = TimeSource.Monotonic.markNow()
                    stream.close()
                    closing.elapsedNow().inWholeMilliseconds shouldBeLessThan 1_000L
                    stream.terminalResult?.terminationReason shouldBe TerminationReason.CANCELLED
                    reading.await()
                    closing.elapsedNow().inWholeMilliseconds shouldBeLessThan 300L
                }
                val abandoned = TimeSource.Monotonic.markNow()
                while (mipThreads() > 0 && abandoned.elapsedNow().inWholeMilliseconds < 10_000) delay(50)
                mipThreads() shouldBe 0
            }
        }
    })

private const val LIMIT_MILLIS = 60_000L
