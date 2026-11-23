package io.github.gciatto.ktconstraints.solve.portfolio

import io.github.gciatto.ktconstraints.core.Rational
import io.github.gciatto.ktconstraints.core.global.counting.AllDifferent
import io.github.gciatto.ktconstraints.core.integer.IntLiteral
import io.github.gciatto.ktconstraints.core.integer.arithmetic.IntSum
import io.github.gciatto.ktconstraints.core.integer.relation.IntLessOrEqual
import io.github.gciatto.ktconstraints.core.real.RealLiteral
import io.github.gciatto.ktconstraints.core.real.relation.RealLessThan
import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.solve.result.SolveStatus
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.gciatto.kt.math.BigInteger

/** The bundled JVM routes, ranked by the default policy, pick the engine each model family suits. */
class DefaultBackendsJvmSpec :
    FunSpec({
        val linear =
            ConstraintModel.of {
                val x = intVar("x", 0..9)
                val y = intVar("y", 0..9)
                add(IntLessOrEqual(IntSum(x, y), IntLiteral(10)))
                maximize(IntSum(x, IntSum(x, y)))
            }
        val globals = ConstraintModel.of { add(AllDifferent(List(3) { intVar("x$it", 0..2) })) }
        val reals = ConstraintModel.of { add(RealLessThan(RealLiteral(Rational.of(1, 2)), realVar("r"))) }

        test("defaults/jvm-ranks-routes-by-model-features") {
            DefaultBackends.load().use { loaded ->
                loaded.registry
                    .rank(linear)
                    .best.backend.descriptor.id shouldBe "scip/or-tools"
                loaded.registry
                    .rank(globals)
                    .best.backend.descriptor.id shouldBe "cp-sat/direct"
                loaded.registry
                    .rank(reals)
                    .best.backend.descriptor.id shouldBe "z3/direct"
            }
        }

        test("defaults/solve-auto-proves-an-optimum") {
            val result = solveAuto(linear)
            result.status shouldBe SolveStatus.OPTIMAL
            result.objectiveValue shouldBe BigInteger.of(19)
        }
    })
