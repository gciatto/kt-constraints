package io.github.gciatto.ktconstraints.solve.auto.examples.tutorials

import io.github.gciatto.ktconstraints.solve.result.SolveStatus
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/** Runs the complete first-model tutorial on each target. */
class SendMoreMoneySpec :
    FunSpec({
        test("solve the alphametic and reject the extra digit constraint") {
            val (digits, twistStatus) = sendMoreMoney()
            digits shouldBe listOf(9, 5, 6, 7, 1, 0, 8, 2)
            twistStatus shouldBe SolveStatus.INFEASIBLE
        }
    })
