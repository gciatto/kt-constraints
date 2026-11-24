package io.github.gciatto.ktconstraints.solve.auto.examples.problems

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.gciatto.kt.math.BigInteger

/** Executable JobShop cookbook example. */
class JobShopSpec :
    FunSpec({
        test("minimize the makespan of two jobs on two machines") {
            val schedule = solveJobShop()
            schedule.makespan shouldBe BigInteger.of(3)
            schedule.starts shouldBe mapOf("A-machine-0" to 0, "A-machine-1" to 2, "B-machine-1" to 0, "B-machine-0" to 2)
        }
    })
