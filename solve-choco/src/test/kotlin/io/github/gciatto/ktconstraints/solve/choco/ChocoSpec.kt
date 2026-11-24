package io.github.gciatto.ktconstraints.solve.choco

import io.github.gciatto.ktconstraints.core.Rational
import io.github.gciatto.ktconstraints.core.bool.BoolNot
import io.github.gciatto.ktconstraints.core.bool.BoolOr
import io.github.gciatto.ktconstraints.core.domain.IntDomain
import io.github.gciatto.ktconstraints.core.global.counting.AllDifferent
import io.github.gciatto.ktconstraints.core.global.counting.BoolCardinality
import io.github.gciatto.ktconstraints.core.global.counting.CardinalityRelation
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
import io.github.gciatto.ktconstraints.solve.choco.translation.ChocoTranslation
import io.github.gciatto.ktconstraints.solve.result.SolveStatus
import io.github.gciatto.ktconstraints.solve.result.TerminationReason
import io.github.gciatto.ktconstraints.solve.spi.exception.UnsupportedModelException
import io.github.gciatto.ktconstraints.solve.stream.SolutionFound
import io.github.gciatto.ktconstraints.solve.stream.SolveCompleted
import io.github.gciatto.ktconstraints.test.solve.differentialContract
import io.github.gciatto.ktconstraints.test.solve.oracle.enumerate
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
            listOf<Pair<String, ConstraintScope.() -> Unit>>(
                "domain-bound" to { intVar("x", IntDomain.of(BigInteger.ZERO, BigInteger.TWO.pow(24))) },
                "literal" to {
                    val x = intVar("x", 0..1)
                    add(IntLessOrEqual(x, IntLiteral(BigInteger.TWO.pow(40))))
                },
                "sum-magnitude" to {
                    val x = intVar("x", 0..6_000_000)
                    val y = intVar("y", 0..6_000_000)
                    add(IntLessOrEqual(IntSum(x, y), IntLiteral(5)))
                },
                "product-interval" to {
                    val x = intVar("x", 0..5_000)
                    add(IntLessOrEqual(IntProduct(x, x), IntLiteral(5)))
                },
                "objective-magnitude" to {
                    val x = intVar("x", 0..6_000_000)
                    maximize(LinearSum(listOf(x), listOf(BigInteger.TWO)))
                },
            ),
        ) { (_, build) ->
            val model = ConstraintModel.of(build)
            ChocoBackend().support(model).isSupported shouldBe false
            ChocoBackend().create().use { solver -> shouldThrow<UnsupportedModelException> { solver.solve(model) } }
        }

        test("choco/reals-and-bit-vectors-are-rejected-before-execution") {
            val scopeModel =
                ConstraintModel.of {
                    val r = realVar("r")
                    add(RealEquality(r, RealLiteral(Rational.of(1, 2))))
                }
            ChocoBackend().support(scopeModel).isSupported shouldBe false
            ChocoBackend().create().use { solver -> shouldThrow<UnsupportedModelException> { solver.solve(scopeModel) } }
            val model =
                ConstraintModel.of {
                    bitVecVar("v", 8)
                }
            ChocoBackend().support(model).isSupported shouldBe false
        }

        test("choco/nested-globals-are-reified-natively") {
            val model =
                ConstraintModel.of {
                    val x = intVar("x", 0..2)
                    val y = intVar("y", 0..2)
                    add(BoolOr(AllDifferent(listOf(x, y)), Table(listOf(x, y), listOf(listOf(BigInteger.ONE, BigInteger.ONE)))))
                }
            val report = ChocoBackend().support(model)
            report.isSupported shouldBe true
            report.losslessDecompositions shouldBe emptyList()
        }

        include(differentialContract("choco") { ChocoBackend().create() })

        test("choco/objectives-near-the-limit-are-exact-and-optimal") {
            val million = BigInteger.of(1_000_000)
            with(ConstraintScope.empty()) {
                val x = intVar("x", -1_000_000..3_000_000)
                val hidden = intVar("hidden", 0..3)
                add(IntLessThan(x, IntSum(IntProduct(hidden, IntLiteral(million)), IntLiteral(-7))))
                maximize(LinearSum(listOf(x, hidden), listOf(BigInteger.of(3), BigInteger.ONE), BigInteger.ONE))
                project(x)
                ChocoBackend().create().use { solver ->
                    val result = solver.solve(build())
                    result.status shouldBe SolveStatus.OPTIMAL
                    result.assignment?.valueOf(x) shouldBe BigInteger.of(2_999_992)
                    result.objectiveValue shouldBe BigInteger.of(8_999_980)
                    result.bestBound shouldBe result.objectiveValue
                    result.assignment?.valueOf(hidden) shouldBe null
                }
            }
        }

        test("choco/long-sums-translate-in-linear-time").config(timeout = 30.seconds) {
            val model =
                ConstraintModel.of {
                    val flags = (0 until 40_000).map { boolVar("b$it") }
                    val xs = (0 until 40_000).map { intVar("x$it", 0..1) }
                    add(BoolCardinality(flags, 1, CardinalityRelation.AT_MOST))
                    add(IntLessOrEqual(LinearSum(xs, xs.map { BigInteger.ONE }), IntLiteral(1)))
                }
            ChocoTranslation(model)
        }

        test("choco/incumbents-strictly-improve") {
            val model =
                ConstraintModel.of {
                    val xs = (0 until 6).map { intVar("x$it", 0..9) }
                    add(AllDifferent(xs))
                    minimize(LinearSum(xs, xs.indices.map { BigInteger.of(it.toLong() + 1) }))
                }
            val (found, result) = ChocoBackend().create().use { enumerate(it, model, objectives = true) }
            result.status shouldBe SolveStatus.OPTIMAL
            result.objectiveValue shouldBe BigInteger.of(35)
            found.map { -(it.single() as BigInteger).toLong() }.shouldBeStrictlyIncreasing()
        }

        test("choco/solution-limit-stops-enumeration") {
            val model =
                ConstraintModel.of {
                    intVar("x", 0..2)
                }
            ChocoBackend().create().use { solver ->
                val (found, result) = enumerate(solver, model, SolveOptions(solutionLimit = 2))
                found.size shouldBe 2
                result.status shouldBe SolveStatus.FEASIBLE
                result.terminationReason shouldBe TerminationReason.RESOURCE_LIMIT
            }
        }

        test("choco/deadline-during-enumeration-is-a-timeout") {
            val model =
                ConstraintModel.of {
                    repeat(9) { intVar("x$it", 0..9) }
                }
            ChocoBackend().create().use { solver ->
                val (found, result) = enumerate(solver, model, SolveOptions(timeoutMillis = 300))
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
            with(ConstraintScope.empty()) first@{
                val x = intVar("x", 0..2)
                with(ConstraintScope.empty()) {
                    val y = intVar("y", 5..6)
                    add(IntLessOrEqual(IntLiteral(6), y))
                    ChocoBackend().create().use { solver ->
                        val left = solver.solutions(this@first.build())
                        val right = solver.solutions(build())
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
            }
        }
    })

/** Thirteen pigeons in twelve holes through pairwise disequalities, which propagate too weakly to refute quickly. */
private fun pigeonholes(): ConstraintModel =
    ConstraintModel.of {
        val pigeons = (0 until 13).map { intVar("p$it", 0..11) }
        for (i in pigeons.indices) {
            for (j in i + 1 until pigeons.size) add(BoolNot(IntEquality(pigeons[i], pigeons[j])))
        }
    }
