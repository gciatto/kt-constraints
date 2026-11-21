package io.github.gciatto.ktconstraints.core

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.gciatto.kt.math.BigInteger

/** Regular, subcircuit, bin packing and 2D non-overlap: semantics, validation and both exact decompositions. */
class RoutingAndPackingGlobalsSpec :
    FunSpec({
        fun ints(vararg values: Long): List<IntExpr> = values.map { IntLiteral(it) }

        fun big(vararg values: Long): List<BigInteger> = values.map { BigInteger.of(it) }

        fun tuples(
            arity: Int,
            range: LongRange,
        ): Sequence<List<Long>> =
            (0 until arity).fold(sequenceOf(emptyList())) { partial, _ -> partial.flatMap { prefix -> range.map { prefix + it } } }

        /** Whether some binding of [formula]'s hidden variables satisfies it; operands are ground. */
        fun satisfiable(formula: BoolExpr): Boolean {
            val hidden = formula.variables.toList()
            val choices: List<List<Any>> =
                hidden.map { variable ->
                    when (variable) {
                        is BoolVar -> listOf(false, true)
                        is IntVar ->
                            variable.domain.intervals.flatMap { interval ->
                                (interval.min.toLongExact()..interval.max.toLongExact()).toList()
                            }
                        else -> error("Unexpected hidden variable $variable")
                    }
                }
            val bindings =
                choices.fold(sequenceOf(emptyList<Any>())) { partial, options -> partial.flatMap { prefix -> options.map { prefix + it } } }
            return bindings.any { values ->
                val valuation =
                    object : Valuation {
                        override fun valueOf(variable: BoolVar): Boolean? = values[hidden.indexOf(variable)] as Boolean

                        override fun valueOf(variable: IntVar): BigInteger? = BigInteger.of(values[hidden.indexOf(variable)] as Long)
                    }
                GroundEvaluator.evaluate(formula, valuation)
            }
        }

        fun exact(global: DecomposableConstraint) {
            val expected = GroundEvaluator.evaluate(global)
            withClue("$global decomposition") { GroundEvaluator.evaluate(global.decomposition) shouldBe expected }
            withClue("$global top-level decomposition") { satisfiable(global.decompose(VariableFactory.empty())) shouldBe expected }
        }

        // Words over {0, 1} without two consecutive ones; state 1 means "just read a one".
        val noDoubleOnes =
            Automaton(
                initial = 0,
                accepting = setOf(0, 1),
                transitions =
                    listOf(
                        Transition(0, BigInteger.ZERO, 0),
                        Transition(0, BigInteger.ONE, 1),
                        Transition(1, BigInteger.ZERO, 0),
                    ),
            )

        test("regular/accepts-words-of-a-deterministic-automaton") {
            GroundEvaluator.evaluate(Regular(ints(0, 1, 0, 1), noDoubleOnes)) shouldBe true
            GroundEvaluator.evaluate(Regular(ints(1, 1), noDoubleOnes)) shouldBe false
            GroundEvaluator.evaluate(Regular(ints(0, 2), noDoubleOnes)) shouldBe false
            GroundEvaluator.evaluate(Regular(emptyList(), noDoubleOnes)) shouldBe true
            noDoubleOnes.states shouldBe 2
            shouldThrow<IllegalArgumentException> {
                Automaton(0, setOf(0), listOf(Transition(0, BigInteger.ZERO, 0), Transition(0, BigInteger.ZERO, 1)))
            }
            shouldThrow<IllegalArgumentException> { Automaton(-1, setOf(0), emptyList()) }
        }

        test("subcircuit/excluded-nodes-loop-and-the-rest-form-one-cycle") {
            GroundEvaluator.evaluate(SubCircuit(ints(1, 0, 2))) shouldBe true
            GroundEvaluator.evaluate(SubCircuit(ints(0, 1, 2))) shouldBe true
            GroundEvaluator.evaluate(SubCircuit(ints(1, 2, 0))) shouldBe true
            GroundEvaluator.evaluate(SubCircuit(ints(1, 0, 3, 2))) shouldBe false
            GroundEvaluator.evaluate(SubCircuit(ints(1, 1))) shouldBe false
            GroundEvaluator.evaluate(SubCircuit(ints(2, 1))) shouldBe false
        }

        test("bin-packing/loads-sum-the-sizes-of-assigned-items") {
            GroundEvaluator.evaluate(BinPacking(ints(0, 1, 0), big(2, 3, 4), ints(6, 3))) shouldBe true
            GroundEvaluator.evaluate(BinPacking(ints(0, 1, 0), big(2, 3, 4), ints(5, 4))) shouldBe false
            GroundEvaluator.evaluate(BinPacking(ints(0, 2), big(1, 1), ints(1, 1))) shouldBe false
            GroundEvaluator.evaluate(BinPacking(emptyList(), emptyList(), ints(0))) shouldBe true
            shouldThrow<IllegalArgumentException> { BinPacking(ints(0), big(-1), ints(0)) }
            shouldThrow<IllegalArgumentException> { BinPacking(ints(0), emptyList(), ints(0)) }
        }

        test("no-overlap-2d/rectangles-may-touch-but-not-overlap") {
            GroundEvaluator.evaluate(NoOverlap2D(ints(0, 2), ints(0, 0), big(2, 1), big(1, 1))) shouldBe true
            GroundEvaluator.evaluate(NoOverlap2D(ints(0, 1), ints(0, 0), big(2, 1), big(1, 1))) shouldBe false
            GroundEvaluator.evaluate(NoOverlap2D(ints(0, 1), ints(0, 1), big(2, 1), big(1, 1))) shouldBe true
            shouldThrow<IllegalArgumentException> { NoOverlap2D(ints(0), ints(0), big(0), big(1)) }
        }

        test("decompositions/are-exact-in-every-context-and-at-top-level") {
            tuples(3, -1L..3L).forEach { exact(Circuit(ints(*it.toLongArray()))) }
            tuples(3, -1L..3L).forEach { exact(SubCircuit(ints(*it.toLongArray()))) }
            tuples(3, -1L..2L).forEach { exact(Regular(ints(*it.toLongArray()), noDoubleOnes)) }
            exact(Regular(emptyList(), noDoubleOnes))
            tuples(3, -1L..2L).forEach { exact(BinPacking(ints(it[0], it[1]), big(2, 1), ints(it[2], 1))) }
            tuples(4, 0L..2L).forEach { exact(NoOverlap2D(ints(it[0], it[1]), ints(it[2], it[3]), big(2, 1), big(1, 2))) }
        }
    })
