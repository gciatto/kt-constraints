package io.github.gciatto.ktconstraints.solve.auto.examples.howto

import io.github.gciatto.ktconstraints.core.Rational
import io.github.gciatto.ktconstraints.solve.result.SolveStatus
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/** The linear-programming guide's example proves its exact optimum with the portable Kotlin route. */
class LinearProgramsSpec :
    FunSpec({
        test("a mixed-integer program reaches its exact optimum") {
            val result = solveMixedIntegerProgram()
            result.status shouldBe SolveStatus.OPTIMAL
            result.realObjectiveValue shouldBe Rational.of(13)
            result.realBestBound shouldBe Rational.of(13)
            result.objectiveValue shouldBe null
            result.metadata.statistics["technique"] shouldBe "branch-and-bound"
        }
        test("a continuous program reaches its exact optimum") {
            val result = solveLinearProgram()
            result.status shouldBe SolveStatus.OPTIMAL
            result.realObjectiveValue shouldBe Rational.of(27, 2)
            result.realBestBound shouldBe Rational.of(27, 2)
            result.objectiveValue shouldBe null
            result.metadata.statistics["technique"] shouldBe "branch-and-bound"
        }
    })
