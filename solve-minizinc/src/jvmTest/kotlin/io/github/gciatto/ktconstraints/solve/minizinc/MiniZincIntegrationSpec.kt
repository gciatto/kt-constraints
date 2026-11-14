package io.github.gciatto.ktconstraints.solve.minizinc

import io.github.gciatto.ktconstraints.core.AllDifferent
import io.github.gciatto.ktconstraints.core.BoolCardinality
import io.github.gciatto.ktconstraints.core.CardinalityRelation
import io.github.gciatto.ktconstraints.core.Element
import io.github.gciatto.ktconstraints.core.IntEquality
import io.github.gciatto.ktconstraints.core.IntLiteral
import io.github.gciatto.ktconstraints.core.LinearSum
import io.github.gciatto.ktconstraints.core.Table
import io.github.gciatto.ktconstraints.model.ConstraintScope
import io.github.gciatto.ktconstraints.solve.ConstraintSolver
import io.github.gciatto.ktconstraints.solve.SolutionFound
import io.github.gciatto.ktconstraints.solve.SolveCompleted
import io.github.gciatto.ktconstraints.solve.SolveOptions
import io.github.gciatto.ktconstraints.solve.TerminationReason
import io.github.gciatto.ktconstraints.test.solve.FiniteOracle
import io.github.gciatto.ktconstraints.test.solve.solverContract
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.gciatto.kt.math.BigInteger
import java.nio.file.Files

/** Actual MiniZinc/Gecode tests; CI explicitly installs and requests the runtime. */
class MiniZincIntegrationSpec : FunSpec({
    val executable = System.getenv("MINIZINC_EXECUTABLE")
    if (executable == null) {
        test("integration/requires-MINIZINC_EXECUTABLE").config(enabled = false) {}
    } else {
        include(solverContract { MiniZincGecodeBackend(executable).create() })
        test("integration/globals-match-the-independent-finite-oracle") {
            val scope = ConstraintScope.empty()
            val x = scope.intVar("x", 0..2)
            val y = scope.intVar("y", 0..2)
            val flag = scope.boolVar("flag")
            scope.add(AllDifferent(listOf(x, y)))
            scope.add(Table(listOf(x, y), listOf(listOf(BigInteger.ZERO, BigInteger.ONE), listOf(BigInteger.ONE, BigInteger.TWO))))
            scope.add(IntEquality(Element(y, listOf(IntLiteral(3), IntLiteral(3), IntLiteral(3))), IntLiteral(3)))
            scope.add(IntEquality(LinearSum(listOf(x, y), listOf(BigInteger.ONE, BigInteger.of(-1))), IntLiteral(-1)))
            scope.add(BoolCardinality(listOf(flag), 1, CardinalityRelation.EXACTLY))
            val model = scope.build()
            suspend fun answers(solver: ConstraintSolver): Set<List<Any?>> = solver.use {
                val values = mutableSetOf<List<Any?>>()
                solver.solutions(model).use { stream ->
                    while (true) when (val event = stream.next()) {
                        is SolutionFound -> values += listOf(event.assignment.valueOf(x), event.assignment.valueOf(y), event.assignment.valueOf(flag))
                        is SolveCompleted -> break
                        null -> error("Missing completion")
                    }
                }
                values
            }
            answers(MiniZincGecodeBackend(executable).create()) shouldBe answers(FiniteOracle())
        }
        test("integration/early-close-kills-the-process-and-deletes-its-workspace") {
            val scope = ConstraintScope.empty()
            scope.intVar("x", 0..1_000_000)
            MiniZincGecodeBackend(executable).create().use { solver ->
                val stream = solver.solutions(scope.build()) as MiniZincStream
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
            val scope = ConstraintScope.empty()
            scope.intVar("x", 0..1_000_000_000)
            MiniZincGecodeBackend(executable).create().use { solver ->
                solver.solve(scope.build(), SolveOptions(timeoutMillis = 20)).terminationReason shouldBe TerminationReason.TIMEOUT
            }
        }
    }
})
