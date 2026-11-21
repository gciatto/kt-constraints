package io.github.gciatto.ktconstraints.solve.kotlin.compiler

import io.github.gciatto.ktconstraints.core.BitVecOperator
import io.github.gciatto.ktconstraints.core.BitVecRelation
import io.github.gciatto.ktconstraints.solve.kotlin.sat.SatStatus
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.gciatto.kt.math.BigInteger

/** Exhaustive three-bit circuits exercise symbolic inputs rather than only constant folding. */
class BitVectorCircuitSpec :
    FunSpec({
        for (operator in BitVecOperator.entries) {
            test("bit-circuit/${operator.name.lowercase()}/matches-the-finite-truth-table") {
                val fixture = BinaryFixture()
                val arithmetic = fixture.arithmetic
                val x = IntArray(3) { fixture.circuit.variable() }
                val y = IntArray(3) { fixture.circuit.variable() }
                val expression = arithmetic.bitVector(operator, x, y)
                for (left in 0..7) {
                    for (right in 0..7) {
                        val assumptions = x.binding(left) + y.binding(right)
                        val expected = expectedBits(operator, left, right)
                        val assignment = requireNotNull(fixture.solver.solve(assumptions).values)
                        arithmetic.decodeBits(expression, assignment) shouldBe BigInteger.of(expected)
                        val equality = arithmetic.bitCompare(BitVecRelation.EQUAL, expression, arithmetic.bits(BigInteger.of(expected), 3))
                        fixture.solver.solve(assumptions + -equality).status shouldBe SatStatus.UNSATISFIABLE
                    }
                }
            }
        }

        for (relation in BitVecRelation.entries) {
            test("bit-circuit/${relation.name.lowercase()}/is-fully-reified") {
                val fixture = BinaryFixture()
                val x = IntArray(3) { fixture.circuit.variable() }
                val y = IntArray(3) { fixture.circuit.variable() }
                val literal = fixture.arithmetic.bitCompare(relation, x, y)
                for (left in 0..7) {
                    for (right in 0..7) {
                        val expected = expectedComparison(relation, left, right)
                        val assumptions = x.binding(left) + y.binding(right)
                        val assignment = requireNotNull(fixture.solver.solve(assumptions).values)
                        fixture.circuit.value(literal, assignment) shouldBe expected
                        fixture.solver.solve(assumptions + if (expected) -literal else literal).status shouldBe SatStatus.UNSATISFIABLE
                    }
                }
            }
        }

        test("bit-circuit/unary-operations-and-integer-conversions") {
            val fixture = BinaryFixture()
            val x = IntArray(3) { fixture.circuit.variable() }
            val not = fixture.arithmetic.bitNot(x)
            val negative = fixture.arithmetic.bitNegate(x)
            val unsigned = fixture.arithmetic.toInteger(x, signed = false)
            val signed = fixture.arithmetic.toInteger(x, signed = true)
            for (value in 0..7) {
                val assignment = requireNotNull(fixture.solver.solve(x.binding(value)).values)
                fixture.arithmetic.decodeBits(not, assignment) shouldBe BigInteger.of(value xor 7)
                fixture.arithmetic.decodeBits(negative, assignment) shouldBe BigInteger.of(-value and 7)
                fixture.arithmetic.decodeInteger(unsigned, assignment) shouldBe BigInteger.of(value)
                fixture.arithmetic.decodeInteger(signed, assignment) shouldBe BigInteger.of(signedValue(value))
            }
        }

        test("bit-circuit/shift-amounts-larger-than-an-int-saturate") {
            val fixture = BinaryFixture()
            val value = fixture.arithmetic.bits(BigInteger.ONE shl 64, 65)
            val amount = fixture.arithmetic.bits(BigInteger.ONE shl 40, 65)
            val left = fixture.arithmetic.bitVector(BitVecOperator.SHIFT_LEFT, value, amount)
            val logical = fixture.arithmetic.bitVector(BitVecOperator.LOGICAL_SHIFT_RIGHT, value, amount)
            val signed = fixture.arithmetic.bitVector(BitVecOperator.ARITHMETIC_SHIFT_RIGHT, value, amount)
            val assignment = requireNotNull(fixture.solver.solve().values)
            fixture.arithmetic.decodeBits(left, assignment) shouldBe BigInteger.ZERO
            fixture.arithmetic.decodeBits(logical, assignment) shouldBe BigInteger.ZERO
            fixture.arithmetic.decodeBits(signed, assignment) shouldBe (BigInteger.ONE shl 65) - BigInteger.ONE
        }
    })

private fun IntArray.binding(value: Int): IntArray = IntArray(size) { bit -> if (value and (1 shl bit) != 0) this[bit] else -this[bit] }

private fun signedValue(value: Int): Int = if (value >= 4) value - 8 else value

private fun expectedBits(operator: BitVecOperator, left: Int, right: Int): Int =
    when (operator) {
        BitVecOperator.ADD -> left + right
        BitVecOperator.SUBTRACT -> left - right
        BitVecOperator.MULTIPLY -> left * right
        BitVecOperator.AND -> left and right
        BitVecOperator.OR -> left or right
        BitVecOperator.XOR -> left xor right
        BitVecOperator.SHIFT_LEFT -> if (right >= 3) 0 else left shl right
        BitVecOperator.LOGICAL_SHIFT_RIGHT -> if (right >= 3) 0 else left shr right
        BitVecOperator.ARITHMETIC_SHIFT_RIGHT -> if (right >= 3) signedValue(left) shr 3 else signedValue(left) shr right
    } and 7

private fun expectedComparison(relation: BitVecRelation, left: Int, right: Int): Boolean =
    when (relation) {
        BitVecRelation.EQUAL -> left == right
        BitVecRelation.UNSIGNED_LESS_THAN -> left < right
        BitVecRelation.UNSIGNED_LESS_OR_EQUAL -> left <= right
        BitVecRelation.SIGNED_LESS_THAN -> signedValue(left) < signedValue(right)
        BitVecRelation.SIGNED_LESS_OR_EQUAL -> signedValue(left) <= signedValue(right)
    }
