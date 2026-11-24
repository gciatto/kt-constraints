package io.github.gciatto.ktconstraints.solve.mip

import io.github.gciatto.ktconstraints.solve.mip.ortools.OrToolsMipStream
import io.github.gciatto.ktconstraints.solve.result.SolveStatus
import io.github.gciatto.ktconstraints.solve.result.TerminationReason
import io.github.gciatto.ktconstraints.solve.spi.withSolver
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlin.time.TimeSource

/** SCIP through OR-Tools: shared MIP route tests plus interruption of an in-flight solve. */
class ScipSpec :
    FunSpec({
        include(mipRoute("scip/or-tools") { OrToolsMipBackend.scip() })

        test("scip/or-tools/close-interrupts-an-in-flight-solve") {
            OrToolsMipBackend.scip().withSolver { solver ->
                val stream = solver.solutions(marketSplit())
                val started = TimeSource.Monotonic.markNow()
                coroutineScope {
                    val reading = async { runCatching { stream.next() } }
                    delay(300)
                    stream.close()
                    reading.await()
                }
                started.elapsedNow().inWholeMilliseconds shouldBeLessThan 5_000L
                stream.terminalResult?.status shouldBe SolveStatus.UNKNOWN
                stream.terminalResult?.terminationReason shouldBe TerminationReason.CANCELLED
                mipThreads() shouldBe 0
            }
        }
    })

internal fun mipThreads(): Int = Thread.getAllStackTraces().keys.count { it.name == OrToolsMipStream.THREAD_NAME && it.isAlive }
