package io.github.gciatto.ktconstraints.core

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.gciatto.kt.math.BigInteger

/** Inverse, circuit, cumulative and 0/1 conversion: direct semantics, validation and exact decompositions. */
class StructuralGlobalsSpec :
    FunSpec({
        fun ints(vararg values: Long): List<IntExpr> = values.map { IntLiteral(it) }

        fun big(vararg values: Long): List<BigInteger> = values.map { BigInteger.of(it) }

        /** Every tuple of [arity] values in [range]. */
        fun tuples(
            arity: Int,
            range: LongRange,
        ): Sequence<List<Long>> =
            (0 until arity).fold(sequenceOf(emptyList())) { partial, _ -> partial.flatMap { prefix -> range.map { prefix + it } } }

        fun agrees(global: DecomposableConstraint) {
            withClue(global) { GroundEvaluator.evaluate(global.decomposition) shouldBe GroundEvaluator.evaluate(global) }
        }

        test("bool-to-int/is-zero-or-one") {
            GroundEvaluator.evaluate(BoolToInt(BoolLiteral(true))) shouldBe BigInteger.ONE
            GroundEvaluator.evaluate(BoolToInt(BoolLiteral(false))) shouldBe BigInteger.ZERO
            BoolToInt(BoolLiteral(true)).sort shouldBe Sort.Integer
        }

        test("inverse/accepts-mutual-inverses-only") {
            GroundEvaluator.evaluate(Inverse(ints(1, 2, 0), ints(2, 0, 1))) shouldBe true
            GroundEvaluator.evaluate(Inverse(ints(1, 2, 0), ints(1, 2, 0))) shouldBe false
            GroundEvaluator.evaluate(Inverse(ints(0, 3), ints(0, 1))) shouldBe false
            GroundEvaluator.evaluate(Inverse(emptyList(), emptyList())) shouldBe true
            shouldThrow<IllegalArgumentException> { Inverse(ints(0), emptyList()) }
        }

        test("circuit/accepts-single-hamiltonian-cycles-only") {
            GroundEvaluator.evaluate(Circuit(ints(1, 2, 0))) shouldBe true
            GroundEvaluator.evaluate(Circuit(ints(1, 0, 2))) shouldBe false
            GroundEvaluator.evaluate(Circuit(ints(1, 0, 3, 2))) shouldBe false
            GroundEvaluator.evaluate(Circuit(ints(0))) shouldBe true
            GroundEvaluator.evaluate(Circuit(ints(1))) shouldBe false
            GroundEvaluator.evaluate(Circuit(emptyList())) shouldBe true
        }

        test("cumulative/bounds-the-resource-profile") {
            val durations = big(2, 1, 2)
            val demands = big(1, 1, 2)
            GroundEvaluator.evaluate(Cumulative(ints(0, 1, 2), durations, demands, BigInteger.TWO)) shouldBe true
            GroundEvaluator.evaluate(Cumulative(ints(0, 0, 3), durations, demands, BigInteger.TWO)) shouldBe true
            GroundEvaluator.evaluate(Cumulative(ints(0, 1, 1), durations, demands, BigInteger.TWO)) shouldBe false
            GroundEvaluator.evaluate(Cumulative(ints(0), big(1), big(3), BigInteger.TWO)) shouldBe false
            shouldThrow<IllegalArgumentException> { Cumulative(ints(0), big(0), big(1), BigInteger.ONE) }
            shouldThrow<IllegalArgumentException> { Cumulative(ints(0), big(1), big(-1), BigInteger.ONE) }
            shouldThrow<IllegalArgumentException> { Cumulative(ints(0), big(1), big(1), BigInteger.of(-1)) }
            shouldThrow<IllegalArgumentException> { Cumulative(ints(0, 1), big(1), big(1), BigInteger.ONE) }
        }

        test("decompositions/agree-with-direct-semantics-exhaustively") {
            tuples(3, -1L..3L).forEach { agrees(Circuit(ints(*it.toLongArray()))) }
            tuples(4, -1L..2L).forEach { agrees(Inverse(ints(it[0], it[1]), ints(it[2], it[3]))) }
            tuples(3, 0L..3L).forEach { agrees(Cumulative(ints(*it.toLongArray()), big(2, 1, 3), big(1, 2, 1), BigInteger.TWO)) }
            tuples(3, 0L..2L).forEach { agrees(Cumulative(ints(*it.toLongArray()), big(1, 2, 1), big(0, 1, 1), BigInteger.ONE)) }
            agrees(Circuit(emptyList()))
            agrees(Inverse(emptyList(), emptyList()))
        }

        test("globals/snapshot-operands-and-retain-structural-equality") {
            val starts = ints(0, 1).toMutableList()
            val cumulative = Cumulative(starts, big(1, 1), big(1, 1), BigInteger.ONE)
            starts.clear()
            cumulative shouldBe Cumulative(ints(0, 1), big(1, 1), big(1, 1), BigInteger.ONE)
            Circuit(ints(1, 0)) shouldBe Circuit(ints(1, 0))
            Inverse(ints(0), ints(0)) shouldBe Inverse(ints(0), ints(0))
            (Inverse(ints(0), ints(0)) == Circuit(ints(0))) shouldBe false
            BoolAnd.of(emptyList()) shouldBe BoolLiteral(true)
            GroundEvaluator.evaluate(BoolAnd.of(List(1000) { BoolLiteral(it >= 0) })) shouldBe true
        }
    })
