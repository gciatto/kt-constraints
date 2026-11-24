package io.github.gciatto.ktconstraints.solve.auto.examples.problems

import io.github.gciatto.ktconstraints.dsl.plus
import io.github.gciatto.ktconstraints.solve.result.SolveStatus
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.gciatto.kt.math.BigInteger

/** Executable JobShop cookbook example. */
class JobShopSpec :
    FunSpec({
        test("minimize the makespan of two jobs on two machines") {
            val (result, starts) = solveJobShop()
            result.status shouldBe SolveStatus.OPTIMAL
            result.objectiveValue shouldBe BigInteger.of(3)
            starts shouldBe
                listOf(BigInteger.ZERO, BigInteger.TWO, BigInteger.ZERO, BigInteger.TWO)
        }
    })
