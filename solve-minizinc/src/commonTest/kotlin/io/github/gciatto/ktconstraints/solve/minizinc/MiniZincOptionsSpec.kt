package io.github.gciatto.ktconstraints.solve.minizinc

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/** MiniZinc tuning becomes driver flags; engines lacking a flag skip it. */
class MiniZincOptionsSpec :
    FunSpec({
        test("minizinc-options/flags") {
            val options = MiniZincOptions(randomSeed = 3, threads = 2, freeSearch = true)
            options.arguments(MiniZincEngine.GECODE) shouldBe listOf("-r", "3", "-p", "2", "-f")
            options.arguments(MiniZincEngine.CBC) shouldBe listOf("-p", "2", "-f")
            MiniZincOptions().arguments(MiniZincEngine.CHUFFED) shouldBe emptyList()
            shouldThrow<IllegalArgumentException> { MiniZincOptions(threads = 0) }
        }
    })
