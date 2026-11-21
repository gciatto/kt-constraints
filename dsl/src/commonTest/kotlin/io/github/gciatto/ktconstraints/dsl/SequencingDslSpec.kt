package io.github.gciatto.ktconstraints.dsl

import io.github.gciatto.ktconstraints.core.BinPacking
import io.github.gciatto.ktconstraints.core.BoolVar
import io.github.gciatto.ktconstraints.core.GroundEvaluator
import io.github.gciatto.ktconstraints.core.IntExpr
import io.github.gciatto.ktconstraints.core.IntLiteral
import io.github.gciatto.ktconstraints.core.IntVar
import io.github.gciatto.ktconstraints.core.NoOverlap2D
import io.github.gciatto.ktconstraints.core.SubCircuit
import io.github.gciatto.ktconstraints.core.Valuation
import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.model.Objective
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.gciatto.kt.math.BigInteger

/** Automata, routing, packing, distinct-value counting and soft constraints in the Kotlin DSL. */
class SequencingDslSpec :
    FunSpec({
        fun ints(vararg values: Long): Array<IntExpr> = values.map { IntLiteral(it) }.toTypedArray()

        fun big(vararg values: Long): List<BigInteger> = values.map { BigInteger.of(it) }

        test("dsl/automaton-builder-and-regular") {
            val noDoubleOnes =
                automaton(initial = 0, accepting = setOf(0, 1)) {
                    transition(from = 0, on = 0, to = 0)
                    transition(from = 0, on = 1, to = 1)
                    transition(from = 1, on = 0, to = 0)
                }
            noDoubleOnes.states shouldBe 2
            GroundEvaluator.evaluate(regular(noDoubleOnes, *ints(1, 0, 1))) shouldBe true
            GroundEvaluator.evaluate(regular(noDoubleOnes, ints(1, 1).toList())) shouldBe false
        }

        test("dsl/routing-and-packing") {
            subCircuit(*ints(1, 0, 2)).shouldBeInstanceOf<SubCircuit>()
            GroundEvaluator.evaluate(subCircuit(*ints(1, 0, 2))) shouldBe true
            binPacking(ints(0, 1).toList(), big(2, 3), ints(2, 3).toList()).shouldBeInstanceOf<BinPacking>()
            noOverlap2D(ints(0, 1).toList(), ints(0, 0).toList(), big(1, 1), big(1, 1)).shouldBeInstanceOf<NoOverlap2D>()
        }

        test("dsl/distinct-values") {
            GroundEvaluator.evaluate(nValue(*ints(3, 1, 3, 2, 1))).toString() shouldBe "3"
            GroundEvaluator.evaluate(nValue()).toString() shouldBe "0"
        }

        test("dsl/soft-constraints-minimize-the-violated-weight") {
            lateinit var penalty: IntExpr
            val model =
                ConstraintModel.of {
                    val x = intVar("x", 0..3)
                    penalty = minimizeViolations((x ge 2) to 3, (x le 0) to 1)
                }
            (model.objective as Objective.Minimize).expression shouldBe penalty
            val violated = { value: Long -> GroundEvaluator.evaluate(penalty, Single(value)).toString() }
            violated(0) shouldBe "3"
            violated(1) shouldBe "4"
            violated(3) shouldBe "1"
        }
    })

/** Binds every integer variable to [value]. */
private class Single(
    private val value: Long,
) : Valuation {
    override fun valueOf(variable: BoolVar): Boolean? = null

    override fun valueOf(variable: IntVar): BigInteger = BigInteger.of(value)
}
