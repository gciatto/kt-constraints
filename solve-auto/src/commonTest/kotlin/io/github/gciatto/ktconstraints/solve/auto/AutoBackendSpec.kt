package io.github.gciatto.ktconstraints.solve.auto

import io.github.gciatto.ktconstraints.core.integer.IntVar
import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.solve.SolveOptions
import io.github.gciatto.ktconstraints.solve.SolverCapability
import io.github.gciatto.ktconstraints.solve.SolverDescriptor
import io.github.gciatto.ktconstraints.solve.assignment.Assignment
import io.github.gciatto.ktconstraints.solve.kotlin.KotlinBackend
import io.github.gciatto.ktconstraints.solve.result.SolveResult
import io.github.gciatto.ktconstraints.solve.result.SolveStatus
import io.github.gciatto.ktconstraints.solve.result.TerminationReason
import io.github.gciatto.ktconstraints.solve.spi.SolverBackend
import io.github.gciatto.ktconstraints.solve.spi.exception.BackendExecutionException
import io.github.gciatto.ktconstraints.solve.spi.exception.UnsupportedModelException
import io.github.gciatto.ktconstraints.solve.spi.ranking.RankingPolicy
import io.github.gciatto.ktconstraints.solve.spi.withSolver
import io.github.gciatto.ktconstraints.test.solve.differentialContract
import io.github.gciatto.ktconstraints.test.solve.incrementalContract
import io.github.gciatto.ktconstraints.test.solve.solverContract
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.gciatto.kt.math.BigInteger
import kotlin.time.TimeSource

/** Automatic backends: fallback and race never turn a timeout or a failed backend into a proof. */
class AutoBackendSpec :
    FunSpec({
        val satisfaction = ConstraintModel.of { intVar("x", 0..9) }
        val optimization = ConstraintModel.of { minimize(intVar("x", 0..9)) }

        fun x(model: ConstraintModel) = model.variables.single() as IntVar

        fun feasible(
            value: Long,
            optimal: Boolean = false,
            timedOut: Boolean = false,
        ): (ConstraintModel, SolverDescriptor, Boolean) -> SolveResult =
            { model, descriptor, _ ->
                SolveResult(
                    if (optimal) SolveStatus.OPTIMAL else SolveStatus.FEASIBLE,
                    Assignment.of(integers = mapOf(x(model) to BigInteger.of(value))),
                    descriptor,
                    if (timedOut) TerminationReason.TIMEOUT else TerminationReason.COMPLETED,
                    objectiveValue = model.objective?.let { BigInteger.of(value) },
                )
            }
        val unknown: (ConstraintModel, SolverDescriptor, Boolean) -> SolveResult =
            { _, descriptor, timedOut ->
                SolveResult(SolveStatus.UNKNOWN, null, descriptor, if (timedOut) TerminationReason.TIMEOUT else TerminationReason.COMPLETED)
            }
        val infeasible: (ConstraintModel, SolverDescriptor, Boolean) -> SolveResult =
            { _, descriptor, _ -> SolveResult(SolveStatus.INFEASIBLE, null, descriptor) }
        val broken: (ConstraintModel, SolverDescriptor, Boolean) -> SolveResult =
            { _, descriptor, _ -> throw BackendExecutionException("${descriptor.id} crashed") }

        fun routes(vararg backends: SolverBackend) = backends.asList()

        suspend fun List<SolverBackend>.fallback(
            model: ConstraintModel,
            options: SolveOptions = SolveOptions.DEFAULT,
        ) = AutoBackend(this, AutoStrategy.Fallback, RankingPolicy.REGISTRATION_ORDER).use { auto ->
            auto.withSolver { it.solve(model, options) }
        }

        suspend fun List<SolverBackend>.race(
            model: ConstraintModel,
            parallelism: Int,
        ) = AutoBackend(this, AutoStrategy.Race(parallelism), RankingPolicy.REGISTRATION_ORDER).use { auto ->
            auto.withSolver { it.solve(model) }
        }

        include(solverContract { AutoBackend(listOf(KotlinBackend())).createSolver() })
        include(incrementalContract { AutoBackend(listOf(KotlinBackend())).createSolver() })
        include(differentialContract("auto/kotlin") { AutoBackend(listOf(KotlinBackend())).createSolver() })

        test("auto/descriptor-names-the-strategy-and-delegated-capabilities") {
            val log = AutoLog()
            val scripted = ScriptedBackend("a", 10, log, script = unknown)
            AutoBackend(listOf(scripted)).descriptor.id shouldBe "auto/fallback"
            val race = AutoBackend(listOf(scripted), AutoStrategy.Race(3))
            race.descriptor.id shouldBe "auto/race"
            race.descriptor.capabilities shouldBe scripted.descriptor.capabilities
            AutoBackend(listOf(KotlinBackend())).descriptor.capabilities shouldContain SolverCapability.INCREMENTAL_ASSERTIONS
        }

        test("auto/without-an-eligible-route-the-model-is-unsupported") {
            val log = AutoLog()
            val auto = AutoBackend(listOf(ScriptedBackend("missing", 10, log, available = false, script = unknown)))
            auto.availability().available shouldBe false
            auto.availability().reason!! shouldContain "missing is missing"
            auto.support(satisfaction).unsupported.single() shouldContain "missing/direct rejected"
            shouldThrow<UnsupportedModelException> { auto.withSolver { it.solve(satisfaction) } }
            AutoBackend(emptyList()).availability().reason shouldBe "No route is registered"
        }

        test("auto/closing-releases-closeable-routes") {
            val log = AutoLog()
            var closed = 0
            val owning =
                object : SolverBackend by ScriptedBackend("owning", 10, log, script = unknown), AutoCloseable {
                    override fun close() {
                        closed++
                    }
                }
            AutoBackend(listOf(owning, ScriptedBackend("plain", 10, log, script = unknown))).close()
            closed shouldBe 1
        }

        test("auto/fallback-moves-on-only-after-unknown-or-failure") {
            val log = AutoLog()
            val result =
                routes(
                    ScriptedBackend("crashing", 10, log, script = broken),
                    ScriptedBackend("undecided", 10, log, script = unknown),
                    ScriptedBackend("answering", 10, log, script = feasible(4)),
                    ScriptedBackend("unused", 10, log, script = feasible(5)),
                    ScriptedBackend("missing", 10, log, available = false, script = feasible(6)),
                ).fallback(satisfaction)
            result.status shouldBe SolveStatus.FEASIBLE
            result.descriptor.engine shouldBe "answering"
            log.created shouldContainExactly listOf("crashing", "undecided", "answering")
            val messages = result.metadata.messages.joinToString("\n")
            messages shouldContain "crashing/direct crashed"
            messages shouldContain "undecided/direct: UNKNOWN"
            messages shouldContain "missing is missing"
        }

        test("auto/fallback-without-an-answer-is-unknown-never-infeasible") {
            val log = AutoLog()
            val result =
                routes(
                    ScriptedBackend("a", 10, log, script = broken),
                    ScriptedBackend("b", 10, log, script = unknown),
                ).fallback(satisfaction)
            result.status shouldBe SolveStatus.UNKNOWN
            result.descriptor.id shouldBe "auto/fallback"
        }

        test("auto/fallback-shares-one-deadline") {
            val log = AutoLog()
            val result =
                routes(ScriptedBackend("slow", 10_000, log, script = unknown), ScriptedBackend("next", 10, log, script = feasible(1)))
                    .fallback(satisfaction, SolveOptions(timeoutMillis = 200))
            result.status shouldBe SolveStatus.UNKNOWN
            result.terminationReason shouldBe TerminationReason.TIMEOUT
            log.created shouldContainExactly listOf("slow")
        }

        test("auto/race-first-proof-wins-and-cancels-the-rest") {
            val log = AutoLog()
            val started = TimeSource.Monotonic.markNow()
            val result =
                routes(ScriptedBackend("slow", 10_000, log, script = feasible(1)), ScriptedBackend("fast", 50, log, script = infeasible))
                    .race(satisfaction, parallelism = 2)
            result.status shouldBe SolveStatus.INFEASIBLE
            result.descriptor.engine shouldBe "fast"
            (started.elapsedNow().inWholeMilliseconds < 5_000) shouldBe true
            log.cancelled shouldContain "slow"
        }

        test("auto/race-keeps-the-best-incumbent-without-claiming-optimality") {
            val log = AutoLog()
            val result =
                routes(
                    ScriptedBackend("worse", 30, log, script = feasible(5, timedOut = true)),
                    ScriptedBackend("better", 60, log, script = feasible(3, timedOut = true)),
                ).race(optimization, parallelism = 2)
            result.status shouldBe SolveStatus.FEASIBLE
            result.objectiveValue shouldBe BigInteger.of(3)
            result.descriptor.engine shouldBe "better"
        }

        test("auto/race-proof-of-optimality-comes-from-one-backend") {
            val log = AutoLog()
            val result =
                routes(
                    ScriptedBackend("incumbent", 10, log, script = feasible(2, timedOut = true)),
                    ScriptedBackend("prover", 80, log, script = feasible(2, optimal = true)),
                ).race(optimization, parallelism = 2)
            result.status shouldBe SolveStatus.OPTIMAL
            result.descriptor.engine shouldBe "prover"
        }

        test("auto/race-failures-are-not-evidence") {
            val log = AutoLog()
            val result =
                routes(ScriptedBackend("crashing", 10, log, script = broken), ScriptedBackend("undecided", 30, log, script = unknown))
                    .race(satisfaction, parallelism = 2)
            result.status shouldBe SolveStatus.UNKNOWN
            result.metadata.messages.joinToString() shouldContain "crashing/direct crashed"
        }

        test("auto/race-respects-the-parallelism-limit") {
            val log = AutoLog()
            val result =
                routes(
                    ScriptedBackend("a", 100, log, script = unknown),
                    ScriptedBackend("b", 100, log, script = unknown),
                    ScriptedBackend("c", 100, log, script = feasible(7)),
                ).race(satisfaction, parallelism = 2)
            result.descriptor.engine shouldBe "c"
            log.peak shouldBeLessThanOrEqual 2
            log.created shouldContainExactly listOf("a", "b", "c")
        }
    })
