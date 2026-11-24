package io.github.gciatto.ktconstraints.solve.choco

import io.github.gciatto.ktconstraints.core.global.counting.AllDifferent
import io.github.gciatto.ktconstraints.core.integer.arithmetic.LinearSum
import io.github.gciatto.ktconstraints.core.integer.relation.IntLessThan
import io.github.gciatto.ktconstraints.model.ConstraintScope
import io.github.gciatto.ktconstraints.solve.SolveOptions
import io.github.gciatto.ktconstraints.solve.spi.withSolver
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
            ConstraintScope.empty {
                val xs = List(3) { intVar("x$it", 0..3) }
                add(AllDifferent(xs))
                add(IntLessThan(xs[0], xs[2]))
                val satisfaction = buildModel()
                val options = SolveOptions() + ChocoOptions(search)
                val (found, _) = ChocoBackend().withSolver { enumerate(it, satisfaction, options) }
                val (expected, _) = FiniteOracle().use { enumerate(it, satisfaction) }
                found.toSet() shouldBe expected.toSet()
                found.size shouldBe expected.size

                maximize(LinearSum(xs, listOf(1, 2, 3).map { BigInteger.of(it) }))
                val optimization = buildModel()
                ChocoBackend().withSolver { it.solve(optimization, options) }.objectiveValue shouldBe
                    FiniteOracle().use { it.solve(optimization) }.objectiveValue
            }
        }
    })
