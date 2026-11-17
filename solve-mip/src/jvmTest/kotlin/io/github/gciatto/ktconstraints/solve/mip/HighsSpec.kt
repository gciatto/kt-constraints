package io.github.gciatto.ktconstraints.solve.mip

import io.github.gciatto.ktconstraints.solve.SolveStatus
import io.github.gciatto.ktconstraints.solve.TerminationReason
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.shouldBe
import kotlin.time.TimeSource

/** HiGHS through OR-Tools: shared MIP route tests plus the mandatory per-solve limit that bounds abandoned solves. */
class HighsSpec :
    FunSpec({
        include(mipRoute("highs/or-tools") { OrToolsMipBackend.highs(LIMIT_MILLIS) })

        test("highs/or-tools/requires-a-positive-solve-limit") {
            shouldThrow<IllegalArgumentException> { OrToolsMipBackend.highs(0) }
            OrToolsMipBackend.highs(LIMIT_MILLIS).solveLimitMillis shouldBe LIMIT_MILLIS
        }

        test("highs/or-tools/backend-limit-is-a-resource-limit") {
            OrToolsMipBackend.highs(300).create().use { solver ->
                val started = TimeSource.Monotonic.markNow()
                val result = solver.solve(marketSplit())
                started.elapsedNow().inWholeMilliseconds shouldBeLessThan 10_000L
                result.status shouldBe SolveStatus.UNKNOWN
                result.terminationReason shouldBe TerminationReason.RESOURCE_LIMIT
            }
        }

        test("highs/or-tools/close-returns-at-once-and-the-solve-ends-at-its-limit") {
            OrToolsMipBackend.highs(500).create().use { solver ->
                val stream = solver.solutions(marketSplit())
                val reader = Thread { kotlinx.coroutines.runBlocking { runCatching { stream.next() } } }.apply { start() }
                Thread.sleep(100)
                val closing = TimeSource.Monotonic.markNow()
                stream.close()
                closing.elapsedNow().inWholeMilliseconds shouldBeLessThan 1_000L
                stream.terminalResult?.terminationReason shouldBe TerminationReason.CANCELLED
                reader.join(5_000)
                val abandoned = TimeSource.Monotonic.markNow()
                while (mipThreads() > 0 && abandoned.elapsedNow().inWholeMilliseconds < 10_000) Thread.sleep(50)
                mipThreads() shouldBe 0
            }
        }
    })

private const val LIMIT_MILLIS = 60_000L
