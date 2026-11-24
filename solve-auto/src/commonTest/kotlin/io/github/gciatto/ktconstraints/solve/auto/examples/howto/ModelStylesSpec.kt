package io.github.gciatto.ktconstraints.solve.auto.examples.howto

import io.github.gciatto.ktconstraints.solve.auto.AutoBackend
import io.github.gciatto.ktconstraints.solve.result.SolveStatus
import io.github.gciatto.ktconstraints.solve.result.TerminationReason
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.gciatto.kt.math.BigInteger

/** Executable models shared by the programming-style, optimization and limit guides. */
class ModelStylesSpec :
    FunSpec({
        test("receiver DSL and explicit factories build equivalent constraints") {
            val model = receiverModel()
            val explicitAnswer = solveWithFactories()
            AutoBackend.load().use { auto -> auto.createSolver().use { it.solve(model) } }.status shouldBe SolveStatus.FEASIBLE
            explicitAnswer shouldBe BigInteger.of(3)
        }
        test("maximize an integer objective") {
            val (result, xValue) = maximizeInteger()
            result.status shouldBe SolveStatus.OPTIMAL
            result.objectiveValue shouldBe BigInteger.of(3)
            xValue shouldBe BigInteger.of(3)
        }
        test("minimize weighted violations") {
            val (result, xValue) = minimizePreferences()
            result.status shouldBe SolveStatus.OPTIMAL
            result.objectiveValue shouldBe BigInteger.ONE
            xValue shouldBe BigInteger.ONE
        }
        test("a zero budget gives no feasibility proof") {
            val result = solveWithZeroBudget()
            result.status shouldBe SolveStatus.UNKNOWN
            result.terminationReason shouldBe TerminationReason.TIMEOUT
            result.assignment shouldBe null
        }
        test("regular shift sequences accept rest between working days") {
            val accepted = solveShifts(listOf(1, 0, 1))
            val rejected = solveShifts(listOf(1, 1, 0))
            accepted shouldBe SolveStatus.FEASIBLE
            rejected shouldBe SolveStatus.INFEASIBLE
        }
    })
