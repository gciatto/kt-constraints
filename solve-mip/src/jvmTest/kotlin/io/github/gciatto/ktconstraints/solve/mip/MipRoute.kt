package io.github.gciatto.ktconstraints.solve.mip

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
import io.kotest.core.spec.style.funSpec
import io.kotest.datatest.withTests
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.shouldBe
import org.gciatto.kt.math.BigInteger
import kotlin.random.Random

/** Shared tests of one OR-Tools MIP route: contract, numeric guards, oracle differentials and exact optima. */
fun mipRoute(
    id: String,
    backend: () -> OrToolsMipBackend,
) = funSpec {
    include(solverContract(enumeration = false) { backend().create() })

    test("$id/runtime-is-available-and-does-not-enumerate") {
        val instance = backend()
        instance.availability().available shouldBe true
        instance.descriptor.id shouldBe id
        instance.descriptor.capabilities shouldBe
            setOf(
                SolverCapability.SATISFACTION,
                SolverCapability.OPTIMIZATION,
                SolverCapability.INTEGER_ARITHMETIC,
                SolverCapability.GLOBAL_CONSTRAINTS,
            )
    }

    test("$id/out-of-range-models-are-rejected-before-execution") {
        val scope = ConstraintScope.empty()
        scope.intVar("x", 0..2_000_000)
        backend().support(scope.build()).isSupported shouldBe false
        backend().create().use { solver -> shouldThrow<UnsupportedModelException> { solver.solve(scope.build()) } }
    }

    withTests(nameFn = { "$id/matches-the-oracle/${it.name}" }, differentialCases()) { case ->
        val scope = ConstraintScope.empty()
        case.build(scope)
        val model = scope.build()
        val (expected, expectedResult) = FiniteOracle().use { enumerate(it, model) }
        val (actual, actualResult) = backend().create().use { enumerate(it, model) }
        actualResult.status shouldBe expectedResult.status
        actualResult.terminationReason shouldBe TerminationReason.COMPLETED
        actualResult.objectiveValue shouldBe expectedResult.objectiveValue
        if (model.objective == null) {
            actual.size shouldBe minOf(1, expected.size)
            actual.forEach { it shouldBeIn expected }
        }
    }

    test("$id/objectives-near-the-limit-are-exact-and-optimal") {
        val scope = ConstraintScope.empty()
        val x = scope.intVar("x", -100_000..300_000)
        val hidden = scope.intVar("hidden", 0..3)
        scope.add(IntLessThan(x, IntSum(IntProduct(IntLiteral(100_000), hidden), IntLiteral(-7))))
        scope.maximize(LinearSum(listOf(x, hidden), listOf(BigInteger.of(3), BigInteger.ONE), BigInteger.ONE))
        scope.project(x)
        backend().create().use { solver ->
            val result = solver.solve(scope.build())
            result.status shouldBe SolveStatus.OPTIMAL
            result.assignment?.valueOf(x) shouldBe BigInteger.of(299_992)
            result.objectiveValue shouldBe BigInteger.of(899_980)
            result.bestBound shouldBe result.objectiveValue
            result.assignment?.valueOf(hidden) shouldBe null
        }
    }

    test("$id/deadline-during-a-hard-search-is-not-infeasibility") {
        backend().create().use { solver ->
            val result = solver.solve(marketSplit(), SolveOptions(timeoutMillis = 300))
            result.status shouldBe SolveStatus.UNKNOWN
            result.terminationReason shouldBe TerminationReason.TIMEOUT
        }
    }
}

/** A market-split instance: few equalities over binaries, notoriously hard for LP-based branch and bound. */
internal fun marketSplit(): ConstraintModel {
    val random = Random(7)
    val scope = ConstraintScope.empty()
    val xs = (0 until 40).map { scope.intVar("x$it", 0..1) }
    repeat(5) {
        val weights = xs.map { BigInteger.of(random.nextInt(100).toLong()) }
        val half = weights.fold(BigInteger.ZERO) { total, weight -> total + weight } / BigInteger.TWO
        scope.add(IntEquality(LinearSum(xs, weights), IntLiteral(half)))
    }
    return scope.build()
}

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
        DifferentialCase("affine-products-and-negative-values") { scope ->
            val x = scope.intVar("x", -3..3)
            val y = scope.intVar("y", -3..3)
            val b = scope.boolVar("b")
            scope.add(BoolEquality(b, IntEquality(IntProduct(IntLiteral(-2), x), y)))
            scope.add(IntLessOrEqual(IntDifference(IntProduct(x, IntNegation(IntLiteral(3))), y), IntLiteral(1)))
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
