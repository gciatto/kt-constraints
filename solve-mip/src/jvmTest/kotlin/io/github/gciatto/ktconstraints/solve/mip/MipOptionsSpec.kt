package io.github.gciatto.ktconstraints.solve.mip

import io.github.gciatto.ktconstraints.core.integer.IntLiteral
import io.github.gciatto.ktconstraints.core.integer.arithmetic.LinearSum
import io.github.gciatto.ktconstraints.core.integer.relation.IntLessOrEqual
import io.github.gciatto.ktconstraints.model.ConstraintScope
import io.github.gciatto.ktconstraints.solve.SolveOptions
import io.github.gciatto.ktconstraints.solve.result.SolveStatus
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withTests
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.gciatto.kt.math.BigInteger

/** Threads keep proofs; a positive gap tolerance forfeits the optimality claim. */
class MipOptionsSpec :
    FunSpec({
        fun knapsack() =
            ConstraintScope.empty().run {
                val xs = List(6) { intVar("x$it", 0..3) }
                val weights = listOf(5, 4, 6, 3, 7, 2).map { BigInteger.of(it) }
                add(IntLessOrEqual(LinearSum(xs, weights), IntLiteral(25)))
                maximize(LinearSum(xs, listOf(9, 7, 10, 5, 12, 3).map { BigInteger.of(it) }))
                buildModel()
            }

        withTests(nameFn = { "mip-options/$it" }, listOf("scip", "highs")) { engine ->
            val backend = if (engine == "scip") OrToolsMipBackend.scip() else OrToolsMipBackend.highs(60_000)
            val exact = backend.create().use { it.solve(knapsack(), SolveOptions() + MipOptions(threads = 2)) }
            exact.status shouldBe SolveStatus.OPTIMAL
            val loose = backend.create().use { it.solve(knapsack(), SolveOptions() + MipOptions(relativeGap = 0.5)) }
            loose.status shouldBe SolveStatus.FEASIBLE
            loose.assignment shouldNotBe null
        }

        test("mip-options/validation") {
            shouldThrow<IllegalArgumentException> { MipOptions(threads = 0) }
            shouldThrow<IllegalArgumentException> { MipOptions(relativeGap = -0.1) }
        }
    })
