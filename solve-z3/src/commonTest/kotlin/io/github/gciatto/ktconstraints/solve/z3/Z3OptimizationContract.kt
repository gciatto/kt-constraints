package io.github.gciatto.ktconstraints.solve.z3

import io.github.gciatto.ktconstraints.core.integer.IntLiteral
import io.github.gciatto.ktconstraints.core.integer.arithmetic.LinearSum
import io.github.gciatto.ktconstraints.core.integer.relation.IntLessThan
import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.solve.ConstraintSolver
import io.github.gciatto.ktconstraints.solve.SolveOptions
import io.github.gciatto.ktconstraints.solve.result.SolveStatus
import io.github.gciatto.ktconstraints.solve.result.TerminationReason
import io.kotest.core.factory.TestFactory
import io.kotest.core.spec.style.funSpec
import io.kotest.matchers.shouldBe
import org.gciatto.kt.math.BigInteger

/** Z3 optimization proofs shared by every Z3 route: optimality needs a final refutation, but not one check per value. */
fun z3OptimizationContract(create: () -> ConstraintSolver): TestFactory =
    funSpec {
        test("z3-optimization/wide-domain-optimum-is-proven-within-the-deadline") {
            val model =
                ConstraintModel.of {
                    val x = intVar("x", 0..1_000_000_000)
                    val y = intVar("y", 0..1_000_000_000)
                    val sum = LinearSum(listOf(x, y), listOf(BigInteger.ONE, BigInteger.ONE), BigInteger.ZERO)
                    add(IntLessThan(sum, IntLiteral(1_500_000_000)))
                    maximize(sum)
                }
            create().use { solver ->
                val result = solver.solve(model, SolveOptions(timeoutMillis = 30_000))
                result.status shouldBe SolveStatus.OPTIMAL
                result.terminationReason shouldBe TerminationReason.COMPLETED
                result.objectiveValue shouldBe BigInteger.of(1_499_999_999)
                result.bestBound shouldBe BigInteger.of(1_499_999_999)
            }
        }

        test("z3-optimization/minimization-over-negative-bounds-is-proven") {
            val model =
                ConstraintModel.of {
                    val x = intVar("x", -1_000_000_000..1_000_000_000)
                    add(IntLessThan(IntLiteral(-777_777_777), x))
                    minimize(x)
                }
            create().use { solver ->
                val result = solver.solve(model, SolveOptions(timeoutMillis = 30_000))
                result.status shouldBe SolveStatus.OPTIMAL
                result.objectiveValue shouldBe BigInteger.of(-777_777_776)
            }
        }
    }
