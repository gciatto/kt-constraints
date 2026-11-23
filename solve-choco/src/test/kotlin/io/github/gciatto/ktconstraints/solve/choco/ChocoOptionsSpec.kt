package io.github.gciatto.ktconstraints.solve.choco

import io.github.gciatto.ktconstraints.core.global.counting.AllDifferent
import io.github.gciatto.ktconstraints.core.integer.arithmetic.LinearSum
import io.github.gciatto.ktconstraints.core.integer.relation.IntLessThan
import io.github.gciatto.ktconstraints.model.ConstraintScope
import io.github.gciatto.ktconstraints.solve.SolveOptions
import io.github.gciatto.ktconstraints.test.solve.oracle.FiniteOracle
import io.github.gciatto.ktconstraints.test.solve.oracle.enumerate
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withTests
import io.kotest.matchers.shouldBe
import org.gciatto.kt.math.BigInteger

/** Every Choco search strategy finds exactly the oracle's solutions and optimum. */
class ChocoOptionsSpec :
    FunSpec({
        withTests(nameFn = { "choco-options/search/$it" }, ChocoSearch.entries) { search ->
            val scope = ConstraintScope.empty()
            val xs = List(3) { scope.intVar("x$it", 0..3) }
            scope.add(AllDifferent(xs))
            scope.add(IntLessThan(xs[0], xs[2]))
            val satisfaction = scope.build()
            val options = SolveOptions() + ChocoOptions(search)
            val (found, _) = ChocoBackend().create().use { enumerate(it, satisfaction, options) }
            val (expected, _) = FiniteOracle().use { enumerate(it, satisfaction) }
            found.toSet() shouldBe expected.toSet()
            found.size shouldBe expected.size

            scope.maximize(LinearSum(xs, listOf(1, 2, 3).map { BigInteger.of(it) }))
            val optimization = scope.build()
            ChocoBackend().create().use { it.solve(optimization, options) }.objectiveValue shouldBe
                FiniteOracle().use { it.solve(optimization) }.objectiveValue
        }
    })
