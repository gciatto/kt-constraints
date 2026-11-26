package io.github.gciatto.ktconstraints.solve.kotlin

import io.github.gciatto.ktconstraints.core.global.counting.AllDifferent
import io.github.gciatto.ktconstraints.core.integer.IntLiteral
import io.github.gciatto.ktconstraints.core.integer.arithmetic.LinearSum
import io.github.gciatto.ktconstraints.core.integer.relation.IntLessOrEqual
import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.solve.result.SolveStatus
import io.github.gciatto.ktconstraints.test.solve.benchmark.BenchmarkCatalogue
import io.github.gciatto.ktconstraints.test.solve.benchmark.LinearCatalogue
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withTests
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.gciatto.kt.math.BigInteger

/** The route picks its technique from the model's structure, and both techniques report integer objectives alike. */
class KotlinTechniqueSpec :
    FunSpec({
        withTests(
            nameFn = { "kotlin-technique/${it.first}" },
            listOf(
                Triple("binary-program-uses-branch-and-cut", BenchmarkCatalogue.knapsack(10), "branch-and-cut"),
                Triple("wide-integer-program-uses-branch-and-cut", LinearCatalogue.boundedKnapsack(6), "branch-and-cut"),
                Triple("mixed-integer-program-uses-branch-and-cut", LinearCatalogue.rationalKnapsack(8), "branch-and-cut"),
                Triple("globals-use-sat", BenchmarkCatalogue.assignment(4), "sat"),
            ),
        ) { (_, instance, technique) ->
            val result = KotlinBackend().createSolver().use { it.solve(instance.model) }
            result.status shouldBe instance.expectedStatus
            result.objectiveValue shouldBe instance.expectedObjective
            result.realObjectiveValue shouldBe instance.expectedRealObjective
            result.metadata.statistics["technique"] shouldBe technique
        }

        test("kotlin-technique/linear-satisfaction-stays-with-sat-for-enumeration") {
            val model =
                ConstraintModel.of {
                    val xs = List(3) { intVar("x$it", 0..5) }
                    add(IntLessOrEqual(LinearSum(xs, List(3) { BigInteger.ONE }), IntLiteral(4)))
                }
            val result = KotlinBackend().createSolver().use { it.solve(model) }
            result.status shouldBe SolveStatus.FEASIBLE
            // A batch satisfaction answer closes the stream at its first witness, before engine statistics are attached.
            result.metadata.statistics["technique"] shouldNotBe "branch-and-cut"
        }

        test("kotlin-technique/logic-with-an-objective-stays-with-sat") {
            val model =
                ConstraintModel.of {
                    val xs = List(3) { intVar("x$it", 0..2) }
                    add(AllDifferent(xs))
                    maximize(LinearSum(xs, listOf(BigInteger.ONE, BigInteger.TWO, BigInteger.of(3))))
                }
            val result = KotlinBackend().createSolver().use { it.solve(model) }
            result.objectiveValue shouldBe BigInteger.of(8)
            result.metadata.statistics["technique"] shouldBe "sat"
        }
    })
