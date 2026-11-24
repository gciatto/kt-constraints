package io.github.gciatto.ktconstraints.solve.auto.examples.problems

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.gciatto.kt.math.BigInteger

/** Executable NurseRostering cookbook example. */
class NurseRosteringSpec :
    FunSpec({
        test("cover three days while minimizing weighted preference violations") {
            val roster = solveNurseRostering()
            roster.penalty shouldBe BigInteger.ONE
            roster.work shouldBe listOf(listOf(0, 1, 0), listOf(1, 0, 1))
        }
    })
