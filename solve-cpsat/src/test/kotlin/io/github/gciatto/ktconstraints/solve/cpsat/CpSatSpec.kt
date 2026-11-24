package io.github.gciatto.ktconstraints.solve.cpsat

import io.github.gciatto.ktconstraints.core.Rational
import io.github.gciatto.ktconstraints.core.bool.BoolOr
import io.github.gciatto.ktconstraints.core.domain.IntDomain
import io.github.gciatto.ktconstraints.core.global.counting.AllDifferent
import io.github.gciatto.ktconstraints.core.global.extensional.Table
import io.github.gciatto.ktconstraints.core.integer.IntLiteral
import io.github.gciatto.ktconstraints.core.integer.arithmetic.IntProduct
import io.github.gciatto.ktconstraints.core.integer.arithmetic.IntSum
import io.github.gciatto.ktconstraints.core.integer.arithmetic.LinearSum
import io.github.gciatto.ktconstraints.core.integer.relation.IntEquality
import io.github.gciatto.ktconstraints.core.integer.relation.IntLessOrEqual
import io.github.gciatto.ktconstraints.core.integer.relation.IntLessThan
import io.github.gciatto.ktconstraints.core.real.RealLiteral
import io.github.gciatto.ktconstraints.core.real.relation.RealEquality
import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.model.ConstraintScope
import io.github.gciatto.ktconstraints.solve.SolveOptions
import io.github.gciatto.ktconstraints.solve.SolverCapability
import io.github.gciatto.ktconstraints.solve.result.SolveStatus
import io.github.gciatto.ktconstraints.solve.result.TerminationReason
import io.github.gciatto.ktconstraints.solve.spi.exception.UnsupportedModelException
import io.github.gciatto.ktconstraints.solve.spi.withSolver
import io.github.gciatto.ktconstraints.solve.stream.SolutionFound
import io.github.gciatto.ktconstraints.test.solve.differentialContract
import io.github.gciatto.ktconstraints.test.solve.oracle.enumerate
import io.github.gciatto.ktconstraints.test.solve.solverContract
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withTests
import io.kotest.matchers.collections.shouldBeStrictlyIncreasing
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.gciatto.kt.math.BigInteger

/** Direct CP-SAT JVM route: shared contract, int64 guards, reification differentials, search lifecycle. */
class CpSatSpec :
    FunSpec({
        include(solverContract { CpSatBackend().createSolver() })

        test("cp-sat/runtime-is-available-and-identifies-the-direct-route") {
            val backend = CpSatBackend()
            backend.availability().available shouldBe true
            backend.descriptor.id shouldBe "cp-sat/direct"
            backend.descriptor.capabilities shouldBe
                setOf(
                    SolverCapability.SATISFACTION,
                    SolverCapability.OPTIMIZATION,
                    SolverCapability.ENUMERATION,
                    SolverCapability.INTEGER_ARITHMETIC,
                    SolverCapability.GLOBAL_CONSTRAINTS,
                )
        }

        withTests(
            nameFn = { "cp-sat/int64-guard/${it.first}" },
            listOf<Pair<String, ConstraintScope.() -> Unit>>(
                "domain-bound" to { intVar("x", IntDomain.of(BigInteger.ZERO, BigInteger.TWO.pow(62))) },
                "literal" to {
                    val x = intVar("x", 0..1)
                    add(IntLessOrEqual(x, IntLiteral(BigInteger.TWO.pow(64))))
                },
                "sum-magnitude" to {
                    val x = intVar("x", IntDomain.of(BigInteger.ZERO, BigInteger.TWO.pow(60)))
                    val y = intVar("y", IntDomain.of(BigInteger.ZERO, BigInteger.TWO.pow(60)))
                    add(IntLessOrEqual(LinearSum(listOf(x, y), listOf(BigInteger.TWO, BigInteger.TWO)), IntLiteral(5)))
                },
                "product-interval" to {
                    val x = intVar("x", IntDomain.of(BigInteger.ZERO, BigInteger.TWO.pow(40)))
                    add(IntLessOrEqual(IntProduct(x, x), IntLiteral(5)))
                },
                "objective-magnitude" to {
                    val x = intVar("x", IntDomain.of(BigInteger.ZERO, BigInteger.TWO.pow(60)))
                    maximize(LinearSum(listOf(x), listOf(BigInteger.of(4))))
                },
            ),
        ) { (_, build) ->
            val model = ConstraintModel.of(build)
            CpSatBackend().support(model).isSupported shouldBe false
            CpSatBackend().withSolver { solver -> shouldThrow<UnsupportedModelException> { solver.solve(model) } }
        }

        test("cp-sat/reals-are-rejected-before-execution") {
            val model =
                ConstraintModel.of {
                    val r = realVar("r")
                    add(RealEquality(r, RealLiteral(Rational.of(1, 2))))
                }
            CpSatBackend().support(model).isSupported shouldBe false
            CpSatBackend().withSolver { solver -> shouldThrow<UnsupportedModelException> { solver.solve(model) } }
        }

        test("cp-sat/bit-vectors-are-rejected-before-execution") {
            val model =
                ConstraintModel.of {
                    bitVecVar("v", 8)
                }
            CpSatBackend().support(model).isSupported shouldBe false
        }

        test("cp-sat/nested-globals-declare-their-decompositions") {
            val model =
                ConstraintModel.of {
                    val x = intVar("x", 0..2)
                    val y = intVar("y", 0..2)
                    add(BoolOr(AllDifferent(listOf(x, y)), Table(listOf(x, y), listOf(listOf(BigInteger.ONE, BigInteger.ONE)))))
                }
            val report = CpSatBackend().support(model)
            report.isSupported shouldBe true
            report.losslessDecompositions.size shouldBe 2
        }

        include(differentialContract("cp-sat") { CpSatBackend().createSolver() })

        test("cp-sat/wide-objectives-are-exact-and-optimal") {
            val big = BigInteger.TWO.pow(40)
            ConstraintScope.empty {
                val x = intVar("x", IntDomain.of(-big, big * BigInteger.of(4)))
                val hidden = intVar("hidden", 0..3)
                add(IntLessThan(x, IntSum(IntProduct(hidden, IntLiteral(big)), IntLiteral(-7))))
                maximize(LinearSum(listOf(x, hidden), listOf(BigInteger.of(3), BigInteger.ONE), BigInteger.ONE))
                project(x)
                CpSatBackend().withSolver { solver ->
                    val result = solver.solve(buildModel())
                    result.status shouldBe SolveStatus.OPTIMAL
                    val best = big * BigInteger.of(3) - BigInteger.of(8)
                    result.assignment?.valueOf(x) shouldBe best
                    result.objectiveValue shouldBe best * BigInteger.of(3) + BigInteger.of(4)
                    result.bestBound shouldBe result.objectiveValue
                    result.assignment?.valueOf(hidden) shouldBe null
                }
            }
        }

        test("cp-sat/incumbents-strictly-improve") {
            val model =
                ConstraintModel.of {
                    val xs = (0 until 6).map { intVar("x$it", 0..9) }
                    add(AllDifferent(xs))
                    minimize(LinearSum(xs, xs.indices.map { BigInteger.of(it.toLong() + 1) }))
                }
            val (found, result) = CpSatBackend().withSolver { enumerate(it, model, objectives = true) }
            result.status shouldBe SolveStatus.OPTIMAL
            result.objectiveValue shouldBe BigInteger.of(35)
            found.map { -(it.single() as BigInteger).toLong() }.shouldBeStrictlyIncreasing()
        }

        test("cp-sat/fixed-workers-and-seed-still-prove-optimality") {
            val model =
                ConstraintModel.of {
                    val x = intVar("x", -5..5)
                    val y = intVar("y", -5..5)
                    add(IntEquality(IntProduct(x, y), IntLiteral(-6)))
                    minimize(IntSum(x, y))
                }
            CpSatBackend(workers = 2, seed = 7).withSolver { solver ->
                val result = solver.solve(model)
                result.status shouldBe SolveStatus.OPTIMAL
                result.objectiveValue shouldBe BigInteger.of(-1)
            }
            shouldThrow<IllegalArgumentException> { CpSatBackend(workers = -1) }
        }

        test("cp-sat/solution-limit-stops-enumeration") {
            val model =
                ConstraintModel.of {
                    intVar("x", 0..2)
                }
            CpSatBackend().withSolver { solver ->
                val (found, result) = enumerate(solver, model, SolveOptions(solutionLimit = 2))
                found.size shouldBe 2
                result.status shouldBe SolveStatus.FEASIBLE
                result.terminationReason shouldBe TerminationReason.RESOURCE_LIMIT
            }
        }

        test("cp-sat/deadline-during-enumeration-is-a-timeout") {
            val model =
                ConstraintModel.of {
                    repeat(9) { intVar("x$it", 0..9) }
                }
            CpSatBackend().withSolver { solver ->
                val (found, result) = enumerate(solver, model, SolveOptions(timeoutMillis = 300))
                (found.size > 0) shouldBe true
                result.status shouldBe SolveStatus.FEASIBLE
                result.terminationReason shouldBe TerminationReason.TIMEOUT
            }
        }

        test("cp-sat/closing-a-stream-stops-its-search-thread") {
            ConstraintScope.empty {
                repeat(9) { intVar("x$it", 0..9) }
                val solver = CpSatBackend().createSolver()
                val stream = solver.solutions(buildModel())
                repeat(3) { (stream.next() is SolutionFound) shouldBe true }
                stream.close()
                stream.terminalResult?.terminationReason shouldBe TerminationReason.CANCELLED
                stream.terminalResult?.status shouldBe SolveStatus.FEASIBLE
                searchThreads() shouldBe 0
                val other = solver.solutions(buildModel())
                (other.next() is SolutionFound) shouldBe true
                solver.close()
                other.terminalResult shouldNotBe null
                searchThreads() shouldBe 0
            }
        }
    })

private fun searchThreads(): Int {
    // Joining is part of close(), so no grace period is needed.
    return Thread.getAllStackTraces().keys.count { it.name.startsWith("kt-constraints-cp-sat") && it.isAlive }
}
