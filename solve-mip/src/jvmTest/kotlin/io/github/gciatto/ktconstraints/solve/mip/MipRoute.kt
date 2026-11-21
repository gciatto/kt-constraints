package io.github.gciatto.ktconstraints.solve.mip

import io.github.gciatto.ktconstraints.core.IntEquality
import io.github.gciatto.ktconstraints.core.IntLessThan
import io.github.gciatto.ktconstraints.core.IntLiteral
import io.github.gciatto.ktconstraints.core.IntProduct
import io.github.gciatto.ktconstraints.core.IntSum
import io.github.gciatto.ktconstraints.core.LinearSum
import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.model.ConstraintScope
import io.github.gciatto.ktconstraints.solve.SolveOptions
import io.github.gciatto.ktconstraints.solve.SolveStatus
import io.github.gciatto.ktconstraints.solve.SolverCapability
import io.github.gciatto.ktconstraints.solve.TerminationReason
import io.github.gciatto.ktconstraints.solve.spi.UnsupportedModelException
import io.github.gciatto.ktconstraints.test.solve.DifferentialCase
import io.github.gciatto.ktconstraints.test.solve.differentialContract
import io.github.gciatto.ktconstraints.test.solve.solverContract
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.funSpec
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

    include(
        differentialContract(
            id,
            enumeration = false,
            DifferentialCase.catalogue().filter {
                it.name !=
                    DifferentialCase.NONLINEAR_PRODUCTS
            },
        ) {
            backend().create()
        },
    )

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
