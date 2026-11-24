package io.github.gciatto.ktconstraints.solve.auto.examples.problems

import io.github.gciatto.ktconstraints.dsl.plus
import io.github.gciatto.ktconstraints.solve.result.SolveStatus
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.gciatto.kt.math.BigInteger

/** Executable NurseRostering cookbook example. */
class NurseRosteringSpec :
    FunSpec({
        test("cover three days while minimizing weighted preference violations") {
            val (result, roster) = solveNurseRostering()
            result.status shouldBe SolveStatus.OPTIMAL
            result.objectiveValue shouldBe BigInteger.ONE
            roster shouldBe
                listOf(
                    listOf(BigInteger.ZERO, BigInteger.ONE, BigInteger.ZERO),
                    listOf(BigInteger.ONE, BigInteger.ZERO, BigInteger.ONE),
                )
        }
    })
