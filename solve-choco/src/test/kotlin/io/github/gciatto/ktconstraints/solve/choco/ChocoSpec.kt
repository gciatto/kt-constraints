package io.github.gciatto.ktconstraints.solve.choco

import io.github.gciatto.ktconstraints.core.AllDifferent
import io.github.gciatto.ktconstraints.core.BoolCardinality
import io.github.gciatto.ktconstraints.core.BoolNot
import io.github.gciatto.ktconstraints.core.BoolOr
import io.github.gciatto.ktconstraints.core.CardinalityRelation
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
import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.model.ConstraintScope
import io.github.gciatto.ktconstraints.solve.SolutionFound
import io.github.gciatto.ktconstraints.solve.SolveCompleted
import io.github.gciatto.ktconstraints.solve.SolveOptions
import io.github.gciatto.ktconstraints.solve.SolveStatus
import io.github.gciatto.ktconstraints.solve.SolverCapability
import io.github.gciatto.ktconstraints.solve.TerminationReason
import io.github.gciatto.ktconstraints.solve.spi.UnsupportedModelException
import io.github.gciatto.ktconstraints.test.solve.differentialContract
import io.github.gciatto.ktconstraints.test.solve.enumerate
import io.github.gciatto.ktconstraints.test.solve.solverContract
import io.github.gciatto.ktconstraints.test.solve.storeContract
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withTests
import io.kotest.matchers.collections.shouldBeStrictlyIncreasing
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.withTimeoutOrNull
import org.gciatto.kt.math.BigInteger
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/** Direct Choco JVM route: shared contract, 32-bit guards, reification differentials, search lifecycle. */
class ChocoSpec :
    FunSpec({
        include(solverContract { ChocoBackend().create() })
        include(storeContract(propagates = true) { ChocoStore() })

        test("choco/runtime-is-available-and-identifies-the-direct-route") {
            val backend = ChocoBackend()
            backend.availability().available shouldBe true
            backend.descriptor.id shouldBe "choco/direct"
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
            nameFn = { "choco/int32-guard/${it.first}" },
            listOf<Pair<String, (ConstraintScope) -> Unit>>(
                "domain-bound" to { scope -> scope.intVar("x", IntDomain.of(BigInteger.ZERO, BigInteger.TWO.pow(24))) },
                "literal" to { scope ->
                    val x = scope.intVar("x", 0..1)
                    scope.add(IntLessOrEqual(x, IntLiteral(BigInteger.TWO.pow(40))))
                },
                "sum-magnitude" to { scope ->
                    val x = scope.intVar("x", 0..6_000_000)
                    val y = scope.intVar("y", 0..6_000_000)
                    scope.add(IntLessOrEqual(IntSum(x, y), IntLiteral(5)))
                },
                "product-interval" to { scope ->
                    val x = scope.intVar("x", 0..5_000)
                    scope.add(IntLessOrEqual(IntProduct(x, x), IntLiteral(5)))
                },
                "objective-magnitude" to { scope ->
                    val x = scope.intVar("x", 0..6_000_000)
                    scope.maximize(LinearSum(listOf(x), listOf(BigInteger.TWO)))
                },
            ),
        ) { (_, build) ->
            val scope = ConstraintScope.empty()
            build(scope)
            val model = scope.build()
            ChocoBackend().support(model).isSupported shouldBe false
            ChocoBackend().create().use { solver -> shouldThrow<UnsupportedModelException> { solver.solve(model) } }
        }

        test("choco/reals-and-bit-vectors-are-rejected-before-execution") {
            val scope = ConstraintScope.empty()
            val r = scope.realVar("r")
            scope.add(RealEquality(r, RealLiteral(Rational.of(1, 2))))
            ChocoBackend().support(scope.build()).isSupported shouldBe false
            ChocoBackend().create().use { solver -> shouldThrow<UnsupportedModelException> { solver.solve(scope.build()) } }
            val vectors = ConstraintScope.empty()
            vectors.bitVecVar("v", 8)
            ChocoBackend().support(vectors.build()).isSupported shouldBe false
        }

        test("choco/nested-globals-are-reified-natively") {
            val scope = ConstraintScope.empty()
            val x = scope.intVar("x", 0..2)
            val y = scope.intVar("y", 0..2)
            scope.add(BoolOr(AllDifferent(listOf(x, y)), Table(listOf(x, y), listOf(listOf(BigInteger.ONE, BigInteger.ONE)))))
            val report = ChocoBackend().support(scope.build())
            report.isSupported shouldBe true
            report.losslessDecompositions shouldBe emptyList()
        }

        include(differentialContract("choco") { ChocoBackend().create() })

        test("choco/objectives-near-the-limit-are-exact-and-optimal") {
            val million = BigInteger.of(1_000_000)
            val scope = ConstraintScope.empty()
            val x = scope.intVar("x", -1_000_000..3_000_000)
            val hidden = scope.intVar("hidden", 0..3)
            scope.add(IntLessThan(x, IntSum(IntProduct(hidden, IntLiteral(million)), IntLiteral(-7))))
            scope.maximize(LinearSum(listOf(x, hidden), listOf(BigInteger.of(3), BigInteger.ONE), BigInteger.ONE))
            scope.project(x)
            ChocoBackend().create().use { solver ->
                val result = solver.solve(scope.build())
                result.status shouldBe SolveStatus.OPTIMAL
                result.assignment?.valueOf(x) shouldBe BigInteger.of(2_999_992)
                result.objectiveValue shouldBe BigInteger.of(8_999_980)
                result.bestBound shouldBe result.objectiveValue
                result.assignment?.valueOf(hidden) shouldBe null
            }
        }

        test("choco/long-sums-translate-in-linear-time").config(timeout = 30.seconds) {
            val scope = ConstraintScope.empty()
            val flags = (0 until 40_000).map { scope.boolVar("b$it") }
            val xs = (0 until 40_000).map { scope.intVar("x$it", 0..1) }
            scope.add(BoolCardinality(flags, 1, CardinalityRelation.AT_MOST))
            scope.add(IntLessOrEqual(LinearSum(xs, xs.map { BigInteger.ONE }), IntLiteral(1)))
            ChocoTranslation(scope.build())
        }

        test("choco/incumbents-strictly-improve") {
            val scope = ConstraintScope.empty()
            val xs = (0 until 6).map { scope.intVar("x$it", 0..9) }
            scope.add(AllDifferent(xs))
            scope.minimize(LinearSum(xs, xs.indices.map { BigInteger.of(it.toLong() + 1) }))
            val (found, result) = ChocoBackend().create().use { enumerate(it, scope.build(), objectives = true) }
            result.status shouldBe SolveStatus.OPTIMAL
            result.objectiveValue shouldBe BigInteger.of(35)
            found.map { -(it.single() as BigInteger).toLong() }.shouldBeStrictlyIncreasing()
        }

        test("choco/solution-limit-stops-enumeration") {
            val scope = ConstraintScope.empty()
            scope.intVar("x", 0..2)
            ChocoBackend().create().use { solver ->
                val (found, result) = enumerate(solver, scope.build(), SolveOptions(solutionLimit = 2))
                found.size shouldBe 2
                result.status shouldBe SolveStatus.FEASIBLE
                result.terminationReason shouldBe TerminationReason.RESOURCE_LIMIT
            }
        }

        test("choco/deadline-during-enumeration-is-a-timeout") {
            val scope = ConstraintScope.empty()
            repeat(9) { scope.intVar("x$it", 0..9) }
            ChocoBackend().create().use { solver ->
                val (found, result) = enumerate(solver, scope.build(), SolveOptions(timeoutMillis = 300))
                (found.size > 0) shouldBe true
                result.status shouldBe SolveStatus.FEASIBLE
                result.terminationReason shouldBe TerminationReason.TIMEOUT
            }
        }

        test("choco/deadline-during-a-hard-refutation-is-not-infeasibility") {
            ChocoBackend().create().use { solver ->
                val result = solver.solve(pigeonholes(), SolveOptions(timeoutMillis = 300))
                result.status shouldBe SolveStatus.UNKNOWN
                result.terminationReason shouldBe TerminationReason.TIMEOUT
            }
        }

        test("choco/coroutine-cancellation-stops-an-in-flight-search") {
            ChocoBackend().create().use { solver ->
                val started = TimeSource.Monotonic.markNow()
                withTimeoutOrNull(300) { solver.solve(pigeonholes()) } shouldBe null
                started.elapsedNow().inWholeMilliseconds.toInt() shouldBeLessThan 5_000
            }
        }

        test("choco/interleaved-streams-are-isolated") {
            val first = ConstraintScope.empty()
            val x = first.intVar("x", 0..2)
            val second = ConstraintScope.empty()
            val y = second.intVar("y", 5..6)
            second.add(IntLessOrEqual(IntLiteral(6), y))
            ChocoBackend().create().use { solver ->
                val left = solver.solutions(first.build())
                val right = solver.solutions(second.build())
                val values = mutableListOf<BigInteger?>()
                values += (left.next() as SolutionFound).assignment.valueOf(x)
                (right.next() as SolutionFound).assignment.valueOf(y) shouldBe BigInteger.of(6)
                values += (left.next() as SolutionFound).assignment.valueOf(x)
                (right.next() as SolveCompleted).result.status shouldBe SolveStatus.FEASIBLE
                values += (left.next() as SolutionFound).assignment.valueOf(x)
                values.toSet() shouldBe setOf(BigInteger.ZERO, BigInteger.ONE, BigInteger.TWO)
                left.close()
                right.close()
            }
        }
    })

/** Thirteen pigeons in twelve holes through pairwise disequalities, which propagate too weakly to refute quickly. */
private fun pigeonholes(): ConstraintModel {
    val scope = ConstraintScope.empty()
    val pigeons = (0 until 13).map { scope.intVar("p$it", 0..11) }
    for (i in pigeons.indices) {
        for (j in i + 1 until pigeons.size) scope.add(BoolNot(IntEquality(pigeons[i], pigeons[j])))
    }
    return scope.build()
}
