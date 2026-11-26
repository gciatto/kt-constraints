package io.github.gciatto.ktconstraints.solve.kotlin.lp

import io.github.gciatto.ktconstraints.core.Rational
import io.github.gciatto.ktconstraints.core.integer.IntToReal
import io.github.gciatto.ktconstraints.core.real.RealExpr
import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.solve.linear.LinearProgram
import io.github.gciatto.ktconstraints.solve.linear.LinearSolution
import io.github.gciatto.ktconstraints.test.solve.benchmark.LinearCatalogue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/** Stopped searches keep a valid incumbent and a proven bound around the optimum. */
class BranchAndBoundSpec :
    FunSpec({
        val weights = listOf(23, 31, 29, 44, 53, 38, 63, 85, 89, 82)
        val values = listOf(92, 57, 49, 68, 60, 43, 67, 84, 87, 72)

        fun knapsack() =
            LinearProgram.of(
                ConstraintModel.of {
                    val picks = weights.indices.map { IntToReal(intVar("x$it", 0..1)) }

                    fun weighted(coefficients: List<Int>): RealExpr =
                        picks.zip(coefficients).map { (x, c) -> x * c }.reduce { a, b -> a + b }
                    add(weighted(weights) le 165)
                    maximize(weighted(values))
                },
            )

        fun score(program: LinearProgram) = requireNotNull(program.objective).terms

        test("branch-and-bound/finds-the-knapsack-optimum") {
            val program = knapsack()
            val outcome = BranchAndBound(program).maximize(score(program))
            outcome.shouldBeInstanceOf<BranchAndBound.Outcome.Optimal>()
            outcome.incumbent.score shouldBe DeltaRational.of(Rational.of(309))
            LinearSolution(program, outcome.incumbent.values).isFeasible shouldBe true
        }

        test("branch-and-bound/stops-with-an-incumbent-below-and-a-bound-above-the-optimum") {
            val optimum = DeltaRational.of(Rational.of(309))
            var stops = 0
            var bounded = 0
            for (polls in 1..200) {
                val program = knapsack()
                var count = 0
                val search = BranchAndBound(program, stopped = { ++count > polls })
                if (runCatching { search.maximize(score(program)) }.exceptionOrNull() !is SimplexStopped) break
                stops++
                val bound = search.bestBound()
                bound?.let {
                    bounded++
                    (it >= optimum) shouldBe true
                }
                search.incumbent?.let {
                    (it.score <= optimum) shouldBe true
                    LinearSolution(program, it.values).isFeasible shouldBe true
                }
            }
            (stops > 5 && bounded > 0) shouldBe true
        }
    
        test("branch-and-bound/cut-rounds-keep-the-optimum") {
            for (instance in listOf(LinearCatalogue.multiKnapsack(12, 1), LinearCatalogue.facilityLocation(4, 8, 2), LinearCatalogue.rationalKnapsack(14, 3))) {
                val program = LinearProgram.of(instance.model)
                val goal = requireNotNull(program.objective)
                val sign = if (goal.maximize) Rational.ONE else Rational.of(-1)
                val search = BranchAndBound(program, cutRounds = 3)
                val outcome = search.maximize(goal.terms.mapValues { it.value * sign })
                outcome.shouldBeInstanceOf<BranchAndBound.Outcome.Optimal>()
                val optimum = outcome.incumbent.score.real * sign + goal.constant
                optimum shouldBe (instance.expectedRealObjective ?: Rational.of(requireNotNull(instance.expectedObjective)))
                search.statistics().getValue("cuts").toInt() shouldBeGreaterThan 0
            }
        }
    })
