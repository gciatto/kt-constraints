package io.github.gciatto.ktconstraints.solve.cpsat

import io.github.gciatto.ktconstraints.core.AllDifferent
import io.github.gciatto.ktconstraints.core.BoolOr
import io.github.gciatto.ktconstraints.core.IntDomain
import io.github.gciatto.ktconstraints.core.IntEquality
import io.github.gciatto.ktconstraints.core.IntLessOrEqual
import io.github.gciatto.ktconstraints.core.IntLessThan
import io.github.gciatto.ktconstraints.core.IntLiteral
import io.github.gciatto.ktconstraints.core.IntProduct
import io.github.gciatto.ktconstraints.core.IntSum
import io.github.gciatto.ktconstraints.core.LinearSum
import io.github.gciatto.ktconstraints.core.Rational
import io.github.gciatto.ktconstraints.core.RealEquality
import io.github.gciatto.ktconstraints.core.RealLiteral
import io.github.gciatto.ktconstraints.core.Table
import io.github.gciatto.ktconstraints.model.ConstraintScope
import io.github.gciatto.ktconstraints.solve.SolutionFound
import io.github.gciatto.ktconstraints.solve.SolveOptions
import io.github.gciatto.ktconstraints.solve.SolveStatus
import io.github.gciatto.ktconstraints.solve.SolverCapability
import io.github.gciatto.ktconstraints.solve.TerminationReason
import io.github.gciatto.ktconstraints.solve.spi.UnsupportedModelException
import io.github.gciatto.ktconstraints.test.solve.differentialContract
import io.github.gciatto.ktconstraints.test.solve.enumerate
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
        include(solverContract { CpSatBackend().create() })

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
            listOf<Pair<String, (ConstraintScope) -> Unit>>(
                "domain-bound" to { scope -> scope.intVar("x", IntDomain.of(BigInteger.ZERO, BigInteger.TWO.pow(62))) },
                "literal" to { scope ->
                    val x = scope.intVar("x", 0..1)
                    scope.add(IntLessOrEqual(x, IntLiteral(BigInteger.TWO.pow(64))))
                },
                "sum-magnitude" to { scope ->
                    val x = scope.intVar("x", IntDomain.of(BigInteger.ZERO, BigInteger.TWO.pow(60)))
                    val y = scope.intVar("y", IntDomain.of(BigInteger.ZERO, BigInteger.TWO.pow(60)))
                    scope.add(IntLessOrEqual(LinearSum(listOf(x, y), listOf(BigInteger.TWO, BigInteger.TWO)), IntLiteral(5)))
                },
                "product-interval" to { scope ->
                    val x = scope.intVar("x", IntDomain.of(BigInteger.ZERO, BigInteger.TWO.pow(40)))
                    scope.add(IntLessOrEqual(IntProduct(x, x), IntLiteral(5)))
                },
                "objective-magnitude" to { scope ->
                    val x = scope.intVar("x", IntDomain.of(BigInteger.ZERO, BigInteger.TWO.pow(60)))
                    scope.maximize(LinearSum(listOf(x), listOf(BigInteger.of(4))))
                },
            ),
        ) { (_, build) ->
            val scope = ConstraintScope.empty()
            build(scope)
            val model = scope.build()
            CpSatBackend().support(model).isSupported shouldBe false
            CpSatBackend().create().use { solver -> shouldThrow<UnsupportedModelException> { solver.solve(model) } }
        }

        test("cp-sat/reals-are-rejected-before-execution") {
            val scope = ConstraintScope.empty()
            val r = scope.realVar("r")
            scope.add(RealEquality(r, RealLiteral(Rational.of(1, 2))))
            CpSatBackend().support(scope.build()).isSupported shouldBe false
            CpSatBackend().create().use { solver -> shouldThrow<UnsupportedModelException> { solver.solve(scope.build()) } }
        }

        test("cp-sat/bit-vectors-are-rejected-before-execution") {
            val scope = ConstraintScope.empty()
            scope.bitVecVar("v", 8)
            CpSatBackend().support(scope.build()).isSupported shouldBe false
        }

        test("cp-sat/nested-globals-declare-their-decompositions") {
            val scope = ConstraintScope.empty()
            val x = scope.intVar("x", 0..2)
            val y = scope.intVar("y", 0..2)
            scope.add(BoolOr(AllDifferent(listOf(x, y)), Table(listOf(x, y), listOf(listOf(BigInteger.ONE, BigInteger.ONE)))))
            val report = CpSatBackend().support(scope.build())
            report.isSupported shouldBe true
            report.losslessDecompositions.size shouldBe 2
        }

        include(differentialContract("cp-sat") { CpSatBackend().create() })

        test("cp-sat/wide-objectives-are-exact-and-optimal") {
            val big = BigInteger.TWO.pow(40)
            val scope = ConstraintScope.empty()
            val x = scope.intVar("x", IntDomain.of(-big, big * BigInteger.of(4)))
            val hidden = scope.intVar("hidden", 0..3)
            scope.add(IntLessThan(x, IntSum(IntProduct(hidden, IntLiteral(big)), IntLiteral(-7))))
            scope.maximize(LinearSum(listOf(x, hidden), listOf(BigInteger.of(3), BigInteger.ONE), BigInteger.ONE))
            scope.project(x)
            CpSatBackend().create().use { solver ->
                val result = solver.solve(scope.build())
                result.status shouldBe SolveStatus.OPTIMAL
                val best = big * BigInteger.of(3) - BigInteger.of(8)
                result.assignment?.valueOf(x) shouldBe best
                result.objectiveValue shouldBe best * BigInteger.of(3) + BigInteger.of(4)
                result.bestBound shouldBe result.objectiveValue
                result.assignment?.valueOf(hidden) shouldBe null
            }
        }

        test("cp-sat/incumbents-strictly-improve") {
            val scope = ConstraintScope.empty()
            val xs = (0 until 6).map { scope.intVar("x$it", 0..9) }
            scope.add(AllDifferent(xs))
            scope.minimize(LinearSum(xs, xs.indices.map { BigInteger.of(it.toLong() + 1) }))
            val (found, result) = CpSatBackend().create().use { enumerate(it, scope.build(), objectives = true) }
            result.status shouldBe SolveStatus.OPTIMAL
            result.objectiveValue shouldBe BigInteger.of(35)
            found.map { -(it.single() as BigInteger).toLong() }.shouldBeStrictlyIncreasing()
        }

        test("cp-sat/fixed-workers-and-seed-still-prove-optimality") {
            val scope = ConstraintScope.empty()
            val x = scope.intVar("x", -5..5)
            val y = scope.intVar("y", -5..5)
            scope.add(IntEquality(IntProduct(x, y), IntLiteral(-6)))
            scope.minimize(IntSum(x, y))
            CpSatBackend(workers = 2, seed = 7).create().use { solver ->
                val result = solver.solve(scope.build())
                result.status shouldBe SolveStatus.OPTIMAL
                result.objectiveValue shouldBe BigInteger.of(-1)
            }
            shouldThrow<IllegalArgumentException> { CpSatBackend(workers = -1) }
        }

        test("cp-sat/solution-limit-stops-enumeration") {
            val scope = ConstraintScope.empty()
            scope.intVar("x", 0..2)
            CpSatBackend().create().use { solver ->
                val (found, result) = enumerate(solver, scope.build(), SolveOptions(solutionLimit = 2))
                found.size shouldBe 2
                result.status shouldBe SolveStatus.FEASIBLE
                result.terminationReason shouldBe TerminationReason.RESOURCE_LIMIT
            }
        }

        test("cp-sat/deadline-during-enumeration-is-a-timeout") {
            val scope = ConstraintScope.empty()
            repeat(9) { scope.intVar("x$it", 0..9) }
            CpSatBackend().create().use { solver ->
                val (found, result) = enumerate(solver, scope.build(), SolveOptions(timeoutMillis = 300))
                (found.size > 0) shouldBe true
                result.status shouldBe SolveStatus.FEASIBLE
                result.terminationReason shouldBe TerminationReason.TIMEOUT
            }
        }

        test("cp-sat/closing-a-stream-stops-its-search-thread") {
            val scope = ConstraintScope.empty()
            repeat(9) { scope.intVar("x$it", 0..9) }
            val solver = CpSatBackend().create()
            val stream = solver.solutions(scope.build())
            repeat(3) { (stream.next() is SolutionFound) shouldBe true }
            stream.close()
            stream.terminalResult?.terminationReason shouldBe TerminationReason.CANCELLED
            stream.terminalResult?.status shouldBe SolveStatus.FEASIBLE
            searchThreads() shouldBe 0
            val other = solver.solutions(scope.build())
            (other.next() is SolutionFound) shouldBe true
            solver.close()
            other.terminalResult shouldNotBe null
            searchThreads() shouldBe 0
        }
    })

private fun searchThreads(): Int {
    // Joining is part of close(), so no grace period is needed.
    return Thread.getAllStackTraces().keys.count { it.name.startsWith("kt-constraints-cp-sat") && it.isAlive }
}
