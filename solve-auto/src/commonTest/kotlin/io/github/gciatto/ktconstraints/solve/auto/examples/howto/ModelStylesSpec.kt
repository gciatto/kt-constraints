package io.github.gciatto.ktconstraints.solve.auto.examples.howto

import io.github.gciatto.ktconstraints.solve.auto.AutoBackend
import io.github.gciatto.ktconstraints.solve.result.SolveStatus
import io.github.gciatto.ktconstraints.solve.result.TerminationReason
import io.github.gciatto.ktconstraints.solve.spi.withSolver
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.gciatto.kt.math.BigInteger

/** Executable models shared by the programming-style, optimization and limit guides. */
class ModelStylesSpec :
    FunSpec({
        test("receiver DSL builds a satisfiable model") {
            val model = receiverModel()
            AutoBackend.load().use { auto -> auto.withSolver { it.solve(model) } }.status shouldBe SolveStatus.FEASIBLE
        }
        test("explicit factories build the same relation") {
            solveWithFactories() shouldBe BigInteger.of(3)
        }
        test("maximize an integer objective") {
            val result = maximizeInteger()
            result.status shouldBe SolveStatus.OPTIMAL
            result.objectiveValue shouldBe BigInteger.of(3)
        }
        test("minimize weighted violations") {
            val result = minimizePreferences()
            result.status shouldBe SolveStatus.OPTIMAL
            result.objectiveValue shouldBe BigInteger.ONE
        }
        test("a zero budget gives no feasibility proof") {
            val result = solveWithZeroBudget()
            result.status shouldBe SolveStatus.UNKNOWN
            result.terminationReason shouldBe TerminationReason.TIMEOUT
            result.assignment shouldBe null
        }
        test("regular shift sequences accept rest between working days") {
            solveShifts(listOf(1, 0, 1)) shouldBe SolveStatus.FEASIBLE
            solveShifts(listOf(1, 1, 0)) shouldBe SolveStatus.INFEASIBLE
        }
    })
