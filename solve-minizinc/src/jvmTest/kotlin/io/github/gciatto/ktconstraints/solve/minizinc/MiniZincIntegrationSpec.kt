package io.github.gciatto.ktconstraints.solve.minizinc

import io.github.gciatto.ktconstraints.core.bool.BoolVar
import io.github.gciatto.ktconstraints.core.global.counting.AllDifferent
import io.github.gciatto.ktconstraints.core.global.counting.BoolCardinality
import io.github.gciatto.ktconstraints.core.global.counting.CardinalityRelation
import io.github.gciatto.ktconstraints.core.global.extensional.Element
import io.github.gciatto.ktconstraints.core.global.extensional.Table
import io.github.gciatto.ktconstraints.core.integer.IntLiteral
import io.github.gciatto.ktconstraints.core.integer.IntVar
import io.github.gciatto.ktconstraints.core.integer.arithmetic.LinearSum
import io.github.gciatto.ktconstraints.core.integer.relation.IntEquality
import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.model.ConstraintScope
import io.github.gciatto.ktconstraints.solve.ConstraintSolver
import io.github.gciatto.ktconstraints.solve.SolveOptions
import io.github.gciatto.ktconstraints.solve.minizinc.runtime.MiniZincStream
import io.github.gciatto.ktconstraints.solve.result.TerminationReason
import io.github.gciatto.ktconstraints.solve.stream.SolutionFound
import io.github.gciatto.ktconstraints.solve.stream.SolveCompleted
import io.github.gciatto.ktconstraints.test.solve.differentialContract
import io.github.gciatto.ktconstraints.test.solve.oracle.FiniteOracle
import io.github.gciatto.ktconstraints.test.solve.solverContract
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeout
import org.gciatto.kt.math.BigInteger
import java.nio.file.Files
import kotlin.coroutines.coroutineContext

/** Actual MiniZinc/Gecode tests; CI explicitly installs and requests the runtime. */
class MiniZincIntegrationSpec :
    FunSpec({
        val executable = System.getenv("MINIZINC_EXECUTABLE")
        if (executable == null) {
            test("integration/requires-MINIZINC_EXECUTABLE").config(enabled = false) {}
        } else {
            include(solverContract { MiniZincGecodeBackend(executable).create() })
            include(
                differentialContract("integration/gecode") {
                    MiniZincGecodeBackend(executable).create()
                },
            )
            test("integration/tuning-options-keep-the-answers") {
                ConstraintScope.empty {
                    val x = intVar("x", 0..3)
                    val y = intVar("y", 0..3)
                    add(AllDifferent(listOf(x, y)))
                    maximize(LinearSum(listOf(x, y), listOf(BigInteger.TWO, BigInteger.ONE)))
                    val tuned = SolveOptions() + MiniZincOptions(randomSeed = 5, threads = 2, freeSearch = true)
                    val result = MiniZincGecodeBackend(executable).create().use { it.solve(buildModel(), tuned) }
                    result.objectiveValue shouldBe BigInteger.of(8)
                }
            }
            test("integration/globals-match-the-independent-finite-oracle") {
                ConstraintScope.empty {
                    val x = intVar("x", 0..2)
                    val y = intVar("y", 0..2)
                    val flag = boolVar("flag")
                    add(AllDifferent(listOf(x, y)))
                    add(Table(listOf(x, y), listOf(listOf(BigInteger.ZERO, BigInteger.ONE), listOf(BigInteger.ONE, BigInteger.TWO))))
                    add(IntEquality(Element(y, listOf(IntLiteral(3), IntLiteral(3), IntLiteral(3))), IntLiteral(3)))
                    add(IntEquality(LinearSum(listOf(x, y), listOf(BigInteger.ONE, BigInteger.of(-1))), IntLiteral(-1)))
                    add(BoolCardinality(listOf(flag), 1, CardinalityRelation.EXACTLY))
                    val model = buildModel()

                    answers(MiniZincGecodeBackend(executable).create(), model, x, y, flag) shouldBe
                        answers(FiniteOracle(), model, x, y, flag)
                }
            }
            test("integration/early-close-kills-the-process-and-deletes-its-workspace") {
                val model =
                    ConstraintModel.of {
                        intVar("x", 0..1_000_000)
                    }
                MiniZincGecodeBackend(executable).create().use { solver ->
                    val stream = solver.solutions(model) as MiniZincStream
                    (stream.next() is SolutionFound) shouldBe true
                    val directory = requireNotNull(stream.workDirectory)
                    val process = ProcessHandle.of(requireNotNull(stream.processId)).orElseThrow()
                    val children = process.descendants().toList()
                    stream.close()
                    stream.terminalResult?.terminationReason shouldBe TerminationReason.CANCELLED
                    Files.exists(directory) shouldBe false
                    process.isAlive shouldBe false
                    children.any { it.isAlive } shouldBe false
                }
            }
            test("integration/owned-deadline-stops-real-execution") {
                val model =
                    ConstraintModel.of {
                        intVar("x", 0..1_000_000_000)
                    }
                MiniZincGecodeBackend(executable).create().use { solver ->
                    solver.solve(model, SolveOptions(timeoutMillis = 20)).terminationReason shouldBe TerminationReason.TIMEOUT
                }
            }
            test("integration/early-close-also-releases-a-long-deadline-timer") {
                val model =
                    ConstraintModel.of {
                        intVar("x", 0..1_000_000)
                    }
                MiniZincGecodeBackend(executable).create().use { solver ->
                    val stream = solver.solutions(model, SolveOptions(timeoutMillis = 60_000))
                    stream.next()
                    val timers = Thread.getAllStackTraces().keys.filter { it.name == "kt-constraints-minizinc-deadline" && it.isAlive }
                    stream.close()
                    withTimeout(1_000) { while (timers.any { it.isAlive }) delay(10) }
                }
            }
        }
    })

// shortcut: detekt 1.23.8 misses suspend calls; remove this exception when its resolver supports Kotlin 2.4.
@Suppress("RedundantSuspendModifier")
private suspend fun answers(
    solver: ConstraintSolver,
    model: ConstraintModel,
    x: IntVar,
    y: IntVar,
    flag: BoolVar,
): Set<List<Any?>> =
    solver.use {
        coroutineContext.ensureActive()
        val values = mutableSetOf<List<Any?>>()
        solver.solutions(model).use { stream ->
            var event = stream.next()
            while (event is SolutionFound) {
                values += listOf(event.assignment.valueOf(x), event.assignment.valueOf(y), event.assignment.valueOf(flag))
                event = stream.next()
            }
            check(event is SolveCompleted) { "Missing completion" }
        }
        values
    }
