package io.github.gciatto.ktconstraints.solve.auto.examples.tutorials

import io.github.gciatto.ktconstraints.solve.result.SolveStatus
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/** Runs the complete first-model tutorial on each target. */
class SendMoreMoneySpec :
    FunSpec({
        test("solve the alphametic") {
            solveSendMoreMoney() shouldBe mapOf('S' to 9, 'E' to 5, 'N' to 6, 'D' to 7, 'M' to 1, 'O' to 0, 'R' to 8, 'Y' to 2)
        }

        test("reject the extra digit constraint") {
            solveSendMoreMoneyWithD(6) shouldBe SolveStatus.INFEASIBLE
        }
    })
