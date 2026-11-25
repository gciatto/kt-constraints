package io.github.gciatto.ktconstraints.solve.minizinc

import io.github.gciatto.ktconstraints.core.global.counting.AllDifferent
import io.github.gciatto.ktconstraints.core.integer.arithmetic.LinearSum
import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.solve.SolveOptions
import io.github.gciatto.ktconstraints.solve.minizinc.process.kmz_group_exists
import io.github.gciatto.ktconstraints.solve.minizinc.runtime.MiniZincStream
import io.github.gciatto.ktconstraints.solve.result.SolveStatus
import io.github.gciatto.ktconstraints.solve.result.TerminationReason
import io.github.gciatto.ktconstraints.solve.spi.exception.BackendUnavailableException
import io.github.gciatto.ktconstraints.solve.spi.withSolver
import io.github.gciatto.ktconstraints.solve.stream.SolutionFound
import io.github.gciatto.ktconstraints.test.solve.differentialContract
import io.github.gciatto.ktconstraints.test.solve.solverContract
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.gciatto.kt.math.BigInteger
import platform.posix.F_OK
import platform.posix.access
import platform.posix.getenv

/** Native MiniZinc runs: a missing executable is diagnosed, and `MINIZINC_EXECUTABLE` enables the real runtime. */
@OptIn(ExperimentalForeignApi::class)
class MiniZincNativeSpec :
    FunSpec({
        test("native/missing-executable-is-unavailable") {
            val backend = MiniZincBackend(MiniZincEngine.GECODE, "/nonexistent/kt-constraints/minizinc")
            backend.availability().available shouldBe false
            backend.availability().reason.isNullOrBlank() shouldBe false
            shouldThrow<BackendUnavailableException> { backend.createSolver() }
        }

        val executable = getenv("MINIZINC_EXECUTABLE")?.toKString()
        if (executable == null) {
            test("native/requires-MINIZINC_EXECUTABLE").config(enabled = false) {}
        } else {
            include(solverContract { MiniZincBackend(MiniZincEngine.GECODE, executable).createSolver() })
            include(differentialContract("native/gecode") { MiniZincBackend(MiniZincEngine.GECODE, executable).createSolver() })

            val engines = getenv("MINIZINC_ENGINES")?.toKString()?.split(',').orEmpty()
            MiniZincEngine.entries.filter { it.engineId in engines }.forEach { engine ->
                test("native/${engine.engineId}-proves-an-optimum") {
                    val model =
                        ConstraintModel.of {
                            val x = intVar("x", 0..3)
                            val y = intVar("y", 0..3)
                            add(AllDifferent(listOf(x, y)))
                            maximize(LinearSum(listOf(x, y), listOf(BigInteger.TWO, BigInteger.ONE)))
                        }
                    val result = MiniZincBackend(engine, executable).withSolver { it.solve(model) }
                    result.status shouldBe SolveStatus.OPTIMAL
                    result.objectiveValue shouldBe BigInteger.of(8)
                }
            }

            test("native/early-close-kills-the-process-group-and-deletes-its-workspace") {
                val model = ConstraintModel.of { intVar("x", 0..1_000_000) }
                MiniZincBackend(MiniZincEngine.GECODE, executable).withSolver { solver ->
                    val stream = solver.solutions(model) as MiniZincStream
                    (stream.next() is SolutionFound) shouldBe true
                    val directory = requireNotNull(stream.workDirectory)
                    val group = requireNotNull(stream.processId).toInt()
                    stream.close()
                    stream.terminalResult?.terminationReason shouldBe TerminationReason.CANCELLED
                    access(directory, F_OK) shouldBe -1
                    // Orphaned solver processes are reaped by init, asynchronously.
                    withTimeout(2_000) { while (kmz_group_exists(group) != 0) delay(10) }
                }
            }

            test("native/owned-deadline-stops-real-execution") {
                val model = ConstraintModel.of { intVar("x", 0..1_000_000_000) }
                MiniZincBackend(MiniZincEngine.GECODE, executable).withSolver { solver ->
                    solver.solve(model, SolveOptions(timeoutMillis = 20)).terminationReason shouldBe TerminationReason.TIMEOUT
                }
            }
        }
    })
