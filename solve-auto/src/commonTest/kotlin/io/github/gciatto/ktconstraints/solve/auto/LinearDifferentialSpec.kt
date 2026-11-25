package io.github.gciatto.ktconstraints.solve.auto

import io.github.gciatto.ktconstraints.core.Rational
import io.github.gciatto.ktconstraints.core.real.RealExpr
import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.solve.kotlin.KotlinBackend
import io.github.gciatto.ktconstraints.solve.result.SolveResult
import io.github.gciatto.ktconstraints.solve.spi.SolverBackend
import io.github.gciatto.ktconstraints.solve.spi.withSolver
import io.github.gciatto.ktconstraints.solve.z3.Z3
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withTests
import io.kotest.matchers.shouldBe
import kotlin.random.Random

/** The pure-Kotlin branch and bound and Z3's optimizer, both exact, agree on seeded random mixed-integer programs. */
class LinearDifferentialSpec :
    FunSpec({
        withTests(nameFn = { "linear-differential/seed=$it" }, (0 until CASES).toList()) { seed ->
            val model = randomProgram(Random(seed))
            val kotlin = solve(KotlinBackend(), model)
            val z3 = Z3.load().use { solve(it, model) }
            kotlin.status shouldBe z3.status
            kotlin.realObjectiveValue?.let {
                if (kotlin.status == z3.status &&
                    z3.realBestBound == z3.realObjectiveValue
                ) {
                    it shouldBe z3.realObjectiveValue
                }
            }
            kotlin.realBestBound shouldBe z3.realBestBound
        }
    })

private const val CASES = 60

private suspend fun solve(
    backend: SolverBackend,
    model: ConstraintModel,
): SolveResult = backend.withSolver { it.solve(model) }

/** Two or three integers in -3..3, one or two reals boxed in -5..5, two to four rows, and a random objective. */
private fun randomProgram(random: Random): ConstraintModel =
    ConstraintModel.of {
        val integers = List(random.nextInt(2, 4)) { intVar("x$it", -3..3).asRealExpr() }
        val reals = List(random.nextInt(1, 3)) { realVar("y$it") }
        reals.forEach {
            add(it ge -5)
            add(it le 5)
        }
        val terms: List<RealExpr> = integers + reals

        fun combination(): RealExpr = terms.map { it * coefficient(random) }.reduce { total, term -> total + term }
        repeat(random.nextInt(2, 5)) {
            val rhs = coefficient(random) * Rational.of(random.nextLong(-4, 5))
            when (random.nextInt(6)) {
                0 -> add(combination() eq rhs)
                1 -> add(combination() lt rhs)
                else -> add(combination() le rhs)
            }
        }
        if (random.nextBoolean()) maximize(combination()) else minimize(combination())
    }

/** A coefficient in -4..4 halved half of the time, so rows are not all integral. */
private fun coefficient(random: Random): Rational = Rational.of(random.nextLong(-4, 5), if (random.nextBoolean()) 1 else 2)
