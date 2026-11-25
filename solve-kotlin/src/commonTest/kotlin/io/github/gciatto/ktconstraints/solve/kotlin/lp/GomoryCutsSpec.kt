package io.github.gciatto.ktconstraints.solve.kotlin.lp

import io.github.gciatto.ktconstraints.core.Rational
import io.github.gciatto.ktconstraints.core.integer.IntToReal
import io.github.gciatto.ktconstraints.core.real.RealExpr
import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.solve.linear.LinearProgram
import io.github.gciatto.ktconstraints.solve.linear.LinearRow
import io.github.gciatto.ktconstraints.solve.linear.LinearSolution
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlin.random.Random

/** Every cut keeps every integral solution and removes the fractional vertex it came from. */
class GomoryCutsSpec :
    FunSpec({
        fun LinearRow.holds(values: List<Rational>): Boolean =
            terms.entries.fold(Rational.ZERO) { sum, (j, c) -> sum + c * values[j] } <= rhs

        test("gomory/cuts-keep-integral-points-and-cut-off-the-vertex") {
            val random = Random(0x6011)
            var generated = 0
            repeat(40) {
                val program =
                    LinearProgram.of(
                        ConstraintModel.of {
                            val xs = (0..2).map { IntToReal(intVar("x$it", 0..4)) }

                            fun random(): RealExpr = xs.map { it * random.nextInt(-4, 7) }.reduce { a, b -> a + b }
                            repeat(3) { add(random() le random.nextInt(3, 15)) }
                            maximize(random())
                        },
                    )
                val simplex = Simplex(program)
                val score = requireNotNull(program.objective).terms
                if (!simplex.check() || !simplex.maximize(score)) return@repeat
                val epsilon = simplex.epsilon()
                val vertex = List(program.columns.size) { simplex.valueOf(it).at(epsilon) }
                val cuts = GomoryCuts.of(simplex, program, limit = 8)
                generated += cuts.size
                cuts.forEach { it.holds(vertex) shouldBe false }
                for (a in 0..4) {
                    for (b in 0..4) {
                        for (c in 0..4) {
                            val point = listOf(a, b, c).map { Rational.of(it.toLong()) }
                            if (LinearSolution(program, point).isFeasible) cuts.forEach { it.holds(point) shouldBe true }
                        }
                    }
                }
            }
            (generated > 10) shouldBe true
        }
    })
