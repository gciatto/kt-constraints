package io.github.gciatto.ktconstraints.solve.choco

import io.github.gciatto.ktconstraints.core.AllDifferent
import io.github.gciatto.ktconstraints.core.BoolAnd
import io.github.gciatto.ktconstraints.core.BoolCardinality
import io.github.gciatto.ktconstraints.core.BoolEquality
import io.github.gciatto.ktconstraints.core.BoolImplies
import io.github.gciatto.ktconstraints.core.BoolLiteral
import io.github.gciatto.ktconstraints.core.BoolNot
import io.github.gciatto.ktconstraints.core.BoolOr
import io.github.gciatto.ktconstraints.core.BoolVar
import io.github.gciatto.ktconstraints.core.CardinalityRelation
import io.github.gciatto.ktconstraints.core.Element
import io.github.gciatto.ktconstraints.core.IntDifference
import io.github.gciatto.ktconstraints.core.IntDomain
import io.github.gciatto.ktconstraints.core.IntEquality
import io.github.gciatto.ktconstraints.core.IntInterval
import io.github.gciatto.ktconstraints.core.IntLessOrEqual
import io.github.gciatto.ktconstraints.core.IntLessThan
import io.github.gciatto.ktconstraints.core.IntLiteral
import io.github.gciatto.ktconstraints.core.IntNegation
import io.github.gciatto.ktconstraints.core.IntProduct
import io.github.gciatto.ktconstraints.core.IntSum
import io.github.gciatto.ktconstraints.core.IntVar
import io.github.gciatto.ktconstraints.core.LinearSum
import io.github.gciatto.ktconstraints.core.Rational
import io.github.gciatto.ktconstraints.core.RealEquality
import io.github.gciatto.ktconstraints.core.RealLiteral
import io.github.gciatto.ktconstraints.core.Table
import io.github.gciatto.ktconstraints.core.Variable
import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.model.ConstraintScope
import io.github.gciatto.ktconstraints.solve.Assignment
import io.github.gciatto.ktconstraints.solve.ConstraintSolver
import io.github.gciatto.ktconstraints.solve.SolutionFound
import io.github.gciatto.ktconstraints.solve.SolveCompleted
import io.github.gciatto.ktconstraints.solve.SolveOptions
import io.github.gciatto.ktconstraints.solve.SolveResult
import io.github.gciatto.ktconstraints.solve.SolveStatus
import io.github.gciatto.ktconstraints.solve.SolverCapability
import io.github.gciatto.ktconstraints.solve.TerminationReason
import io.github.gciatto.ktconstraints.solve.spi.UnsupportedModelException
import io.github.gciatto.ktconstraints.test.solve.FiniteOracle
import io.github.gciatto.ktconstraints.test.solve.solverContract
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withTests
import io.kotest.matchers.collections.shouldBeStrictlyIncreasing
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.withTimeoutOrNull
import org.gciatto.kt.math.BigInteger
import kotlin.time.TimeSource

/** Direct Choco JVM route: shared contract, 32-bit guards, reification differentials, search lifecycle. */
class ChocoSpec :
    FunSpec({
        include(solverContract { ChocoBackend().create() })

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

        withTests(nameFn = { "choco/matches-the-oracle/${it.name}" }, differentialCases()) { case ->
            val scope = ConstraintScope.empty()
            case.build(scope)
            val model = scope.build()
            val (expected, expectedResult) = FiniteOracle().use { enumerate(it, model) }
            val (actual, actualResult) = ChocoBackend().create().use { enumerate(it, model) }
            actualResult.status shouldBe expectedResult.status
            actualResult.objectiveValue shouldBe expectedResult.objectiveValue
            if (model.objective == null) {
                actual.size shouldBe actual.toSet().size
                actual.toSet() shouldBe expected.toSet()
            }
        }

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

private class DifferentialCase(
    val name: String,
    val build: (ConstraintScope) -> Unit,
) {
    override fun toString(): String = name
}

@Suppress("LongMethod")
private fun differentialCases(): List<DifferentialCase> =
    listOf(
        DifferentialCase("reified-relations") { scope ->
            val x = scope.intVar("x", -2..2)
            val y = scope.intVar("y", -2..2)
            val b = scope.boolVar("b")
            scope.add(BoolEquality(b, IntLessThan(x, y)))
            scope.add(BoolOr(b, IntEquality(x, IntLiteral(2))))
            scope.add(BoolNot(IntLessOrEqual(IntNegation(y), IntLiteral(-2))))
        },
        DifferentialCase("nested-all-different") { scope ->
            val xs = (0 until 3).map { scope.intVar("x$it", 0..2) }
            scope.add(BoolNot(AllDifferent(xs)))
            scope.add(IntLessOrEqual(xs[0], xs[1]))
        },
        DifferentialCase("nested-table-and-empty-table") { scope ->
            val x = scope.intVar("x", 0..3)
            val y = scope.intVar("y", 0..3)
            val rows = listOf(listOf(BigInteger.ONE, BigInteger.TWO), listOf(BigInteger.of(3), BigInteger.ZERO))
            scope.add(BoolOr(Table(listOf(x, y), rows), IntEquality(x, y)))
            scope.add(BoolNot(Table(listOf(x), emptyList())))
        },
        DifferentialCase("cardinality-implies-and-conjunction") { scope ->
            val flags = (0 until 3).map { scope.boolVar("f$it") }
            val z = scope.intVar("z", 0..3)
            scope.add(BoolImplies(BoolCardinality(flags, 2, CardinalityRelation.AT_LEAST), IntLessThan(IntLiteral(1), z)))
            scope.add(BoolCardinality(listOf(BoolAnd(flags[0], BoolNot(flags[1])), flags[2]), 1, CardinalityRelation.AT_MOST))
            scope.add(BoolOr(BoolCardinality(flags, 0, CardinalityRelation.EXACTLY), IntLessOrEqual(z, IntLiteral(2))))
        },
        DifferentialCase("products-and-negative-values") { scope ->
            val x = scope.intVar("x", -3..3)
            val y = scope.intVar("y", -3..3)
            val b = scope.boolVar("b")
            scope.add(BoolEquality(b, IntEquality(IntProduct(x, y), IntLiteral(-2))))
            scope.add(IntLessOrEqual(IntDifference(IntProduct(x, IntProduct(x, y)), y), IntLiteral(1)))
        },
        DifferentialCase("element-definedness-is-top-level") { scope ->
            val i = scope.intVar("i", -1..3)
            val b = scope.boolVar("b")
            scope.add(BoolOr(b, IntEquality(Element(i, listOf(IntLiteral(3), IntLiteral(1), IntLiteral(3))), IntLiteral(3))))
        },
        DifferentialCase("domains-with-holes-and-empty-domains") { scope ->
            val x = scope.intVar("x", IntDomain.ofIntervals(listOf(interval(-4, -2), interval(1, 1), interval(5, 6))))
            val y = scope.intVar("y", IntDomain.values(0, 3))
            scope.add(IntLessOrEqual(IntSum(x, y), IntLiteral(5)))
        },
        DifferentialCase("empty-domain-is-infeasible") { scope ->
            scope.intVar("x", IntDomain.empty())
            scope.boolVar("b")
        },
        DifferentialCase("constants-and-hidden-variables") { scope ->
            val x = scope.intVar("x", 0..2)
            val hidden = scope.intVar("hidden", 0..2)
            scope.add(BoolOr(BoolLiteral(false), IntLessOrEqual(x, hidden)))
            scope.add(BoolImplies(BoolLiteral(true), BoolLiteral(true)))
            scope.project(x)
        },
        DifferentialCase("optimization-over-reified-globals") { scope ->
            val xs = (0 until 3).map { scope.intVar("x$it", 0..3) }
            val b = scope.boolVar("b")
            scope.add(BoolEquality(b, AllDifferent(xs)))
            scope.add(IntEquality(Element(xs[0], listOf(IntLiteral(0), IntLiteral(2), IntLiteral(1), IntLiteral(0))), xs[1]))
            scope.maximize(LinearSum(xs, listOf(BigInteger.ONE, BigInteger.of(-2), BigInteger.of(3)), BigInteger.of(-1)))
        },
        DifferentialCase("infeasible-optimization") { scope ->
            val x = scope.intVar("x", 0..3)
            scope.add(IntLessThan(x, IntLiteral(0)))
            scope.minimize(x)
        },
    )

private fun interval(
    min: Long,
    max: Long,
): IntInterval = IntInterval(BigInteger.of(min), BigInteger.of(max))

/** Collects projected solution tuples (or objective values) and the terminal result. */
@Suppress("RedundantSuspendModifier") // detekt misses the suspending SolutionStream.next calls.
private suspend fun enumerate(
    solver: ConstraintSolver,
    model: ConstraintModel,
    options: SolveOptions = SolveOptions.DEFAULT,
    objectives: Boolean = false,
): Pair<List<List<Any?>>, SolveResult> {
    val found = mutableListOf<List<Any?>>()
    solver.solutions(model, options).use { stream ->
        var event = stream.next()
        while (event is SolutionFound) {
            found += if (objectives) listOf(event.objectiveValue) else event.assignment.tuple(model.outputVariables)
            event = stream.next()
        }
        return found to (event as SolveCompleted).result
    }
}

private fun Assignment.tuple(variables: List<Variable>): List<Any?> =
    variables.map {
        when (it) {
            is BoolVar -> valueOf(it)
            is IntVar -> valueOf(it)
            else -> error("Unexpected output variable ${it.name}")
        }
    }

/** Thirteen pigeons in twelve holes through pairwise disequalities, which propagate too weakly to refute quickly. */
private fun pigeonholes(): ConstraintModel {
    val scope = ConstraintScope.empty()
    val pigeons = (0 until 13).map { scope.intVar("p$it", 0..11) }
    for (i in pigeons.indices) {
        for (j in i + 1 until pigeons.size) scope.add(BoolNot(IntEquality(pigeons[i], pigeons[j])))
    }
    return scope.build()
}
