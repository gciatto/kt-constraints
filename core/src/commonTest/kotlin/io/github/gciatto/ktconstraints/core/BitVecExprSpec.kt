package io.github.gciatto.ktconstraints.core

import io.github.gciatto.ktconstraints.core.bitvector.BitVecLiteral
import io.github.gciatto.ktconstraints.core.bitvector.BitVecToInt
import io.github.gciatto.ktconstraints.core.bitvector.BitVecVar
import io.github.gciatto.ktconstraints.core.bitvector.operation.BitVecBinary
import io.github.gciatto.ktconstraints.core.bitvector.operation.BitVecComparison
import io.github.gciatto.ktconstraints.core.bitvector.operation.BitVecNegation
import io.github.gciatto.ktconstraints.core.bitvector.operation.BitVecNot
import io.github.gciatto.ktconstraints.core.bitvector.operation.BitVecOperator
import io.github.gciatto.ktconstraints.core.bitvector.operation.BitVecRelation
import io.github.gciatto.ktconstraints.core.bool.BoolVar
import io.github.gciatto.ktconstraints.core.evaluation.GroundEvaluator
import io.github.gciatto.ktconstraints.core.evaluation.Valuation
import io.github.gciatto.ktconstraints.core.integer.IntVar
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withTests
import io.kotest.matchers.shouldBe
import org.gciatto.kt.math.BigInteger

/** Bit-vectors follow SMT-LIB fixed-width semantics with exact unsigned values of any width. */
class BitVecExprSpec :
    FunSpec({
        test("bitvec/literals-reject-values-outside-the-width") {
            BitVecLiteral(255, 8).sort shouldBe Sort.BitVector(8)
            shouldThrow<IllegalArgumentException> { BitVecLiteral(256, 8) }
            shouldThrow<IllegalArgumentException> { BitVecLiteral(-1, 8) }
            shouldThrow<IllegalArgumentException> { BitVecLiteral(0, 0) }
        }

        test("bitvec/operand-widths-must-match") {
            shouldThrow<IllegalArgumentException> { BitVecBinary(BitVecOperator.ADD, BitVecLiteral(1, 8), BitVecLiteral(1, 16)) }
            shouldThrow<IllegalArgumentException> {
                BitVecComparison(BitVecRelation.EQUAL, BitVecLiteral(1, 8), BitVecLiteral(1, 16))
            }
        }

        withTests(
            nameFn = { "bitvec/operator/${it.operator}/${it.left}-${it.right}-w${it.width}" },
            BinaryCase(BitVecOperator.ADD, 255, 1, 8, 0),
            BinaryCase(BitVecOperator.SUBTRACT, 0, 1, 8, 255),
            BinaryCase(BitVecOperator.MULTIPLY, 16, 16, 8, 0),
            BinaryCase(BitVecOperator.MULTIPLY, 15, 17, 8, 255),
            BinaryCase(BitVecOperator.AND, 12, 10, 4, 8),
            BinaryCase(BitVecOperator.OR, 12, 10, 4, 14),
            BinaryCase(BitVecOperator.XOR, 12, 10, 4, 6),
            BinaryCase(BitVecOperator.SHIFT_LEFT, 1, 7, 8, 128),
            BinaryCase(BitVecOperator.SHIFT_LEFT, 1, 8, 8, 0),
            BinaryCase(BitVecOperator.LOGICAL_SHIFT_RIGHT, 128, 7, 8, 1),
            BinaryCase(BitVecOperator.LOGICAL_SHIFT_RIGHT, 255, 200, 8, 0),
            BinaryCase(BitVecOperator.ARITHMETIC_SHIFT_RIGHT, 128, 1, 8, 192),
            BinaryCase(BitVecOperator.ARITHMETIC_SHIFT_RIGHT, 128, 8, 8, 255),
            BinaryCase(BitVecOperator.ARITHMETIC_SHIFT_RIGHT, 64, 9, 8, 0),
        ) { case ->
            val expression = BitVecBinary(case.operator, BitVecLiteral(case.left, case.width), BitVecLiteral(case.right, case.width))
            GroundEvaluator.evaluate(expression) shouldBe BigInteger.of(case.expected)
        }

        test("bitvec/widths-beyond-64-bits-wrap-exactly") {
            val max = BigInteger.TWO.pow(128) - BigInteger.ONE
            GroundEvaluator.evaluate(BitVecBinary(BitVecOperator.ADD, BitVecLiteral(max, 128), BitVecLiteral(1, 128))) shouldBe
                BigInteger.ZERO
            GroundEvaluator.evaluate(BitVecNegation(BitVecLiteral(1, 128))) shouldBe max
        }

        test("bitvec/not-and-negation") {
            GroundEvaluator.evaluate(BitVecNot(BitVecLiteral(0, 8))) shouldBe BigInteger.of(255)
            GroundEvaluator.evaluate(BitVecNot(BitVecLiteral(170, 8))) shouldBe BigInteger.of(85)
            GroundEvaluator.evaluate(BitVecNegation(BitVecLiteral(1, 8))) shouldBe BigInteger.of(255)
            GroundEvaluator.evaluate(BitVecNegation(BitVecLiteral(0, 8))) shouldBe BigInteger.ZERO
        }

        test("bitvec/signed-and-unsigned-orders-differ") {
            val one = BitVecLiteral(1, 8)
            val minusOne = BitVecLiteral(255, 8)
            GroundEvaluator.evaluate(BitVecComparison(BitVecRelation.UNSIGNED_LESS_THAN, one, minusOne)) shouldBe true
            GroundEvaluator.evaluate(BitVecComparison(BitVecRelation.SIGNED_LESS_THAN, one, minusOne)) shouldBe false
            GroundEvaluator.evaluate(BitVecComparison(BitVecRelation.SIGNED_LESS_THAN, minusOne, one)) shouldBe true
            GroundEvaluator.evaluate(BitVecComparison(BitVecRelation.UNSIGNED_LESS_OR_EQUAL, one, one)) shouldBe true
            GroundEvaluator.evaluate(BitVecComparison(BitVecRelation.SIGNED_LESS_OR_EQUAL, minusOne, minusOne)) shouldBe true
            GroundEvaluator.evaluate(BitVecComparison(BitVecRelation.EQUAL, one, minusOne)) shouldBe false
        }

        test("bitvec/integer-conversion-is-unsigned-or-twos-complement") {
            GroundEvaluator.evaluate(BitVecToInt(BitVecLiteral(255, 8), signed = false)) shouldBe BigInteger.of(255)
            GroundEvaluator.evaluate(BitVecToInt(BitVecLiteral(255, 8), signed = true)) shouldBe BigInteger.of(-1)
            GroundEvaluator.evaluate(BitVecToInt(BitVecLiteral(127, 8), signed = true)) shouldBe BigInteger.of(127)
            BitVecToInt(BitVecLiteral(1, 8), signed = true).sort shouldBe Sort.Integer
        }

        test("bitvec/valuations-bind-in-range-values") {
            val x = VariableFactory.empty().bitVecVar("x", 8)
            x.sort shouldBe Sort.BitVector(8)
            val expression = BitVecBinary(BitVecOperator.ADD, x, BitVecLiteral(1, 8))
            GroundEvaluator.evaluate(expression, bitVectors(x to BigInteger.of(255))) shouldBe BigInteger.ZERO
            shouldThrow<IllegalArgumentException> { GroundEvaluator.evaluate(expression, bitVectors(x to BigInteger.of(256))) }
            shouldThrow<IllegalArgumentException> { GroundEvaluator.evaluate(expression, bitVectors()) }
            shouldThrow<IllegalArgumentException> { VariableFactory.empty().bitVecVar("y", 0) }
        }
    })

private data class BinaryCase(
    val operator: BitVecOperator,
    val left: Long,
    val right: Long,
    val width: Int,
    val expected: Long,
)

private fun bitVectors(vararg bindings: Pair<BitVecVar, BigInteger>): Valuation =
    object : Valuation {
        override fun valueOf(variable: BoolVar): Boolean? = null

        override fun valueOf(variable: IntVar): BigInteger? = null

        override fun valueOf(variable: BitVecVar): BigInteger? = bindings.toMap()[variable]
    }
