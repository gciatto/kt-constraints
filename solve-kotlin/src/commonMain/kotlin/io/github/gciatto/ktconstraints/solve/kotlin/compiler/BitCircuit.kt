package io.github.gciatto.ktconstraints.solve.kotlin.compiler

import io.github.gciatto.ktconstraints.core.BitVecOperator
import org.gciatto.kt.math.BigInteger

/** Boolean implementations of finite-width arithmetic; every output bit is fully determined by its inputs. */
internal class BitCircuit(
    private val circuit: Circuit,
    private val checkpoint: suspend () -> Unit,
) {
    private var operations = 0

    suspend fun constant(
        value: BigInteger,
        width: Int,
    ): IntArray {
        circuit.width(width)
        require(value.signum >= 0 && value.bitLength <= width) { "Bit-vector literal outside its width" }
        val result = IntArray(width)
        for (index in result.indices) {
            tick()
            result[index] = circuit.constant(value.testBit(index))
        }
        return result
    }

    suspend fun extend(
        bits: IntArray,
        width: Int,
        signed: Boolean,
    ): IntArray {
        circuit.width(width)
        require(bits.isNotEmpty())
        val result = IntArray(width)
        for (index in result.indices) {
            tick()
            result[index] = bit(bits, index, signed)
        }
        return result
    }

    suspend fun add(
        left: IntArray,
        right: IntArray,
        carryIn: Int = circuit.falseLiteral,
    ): IntArray {
        require(left.size == right.size && left.isNotEmpty())
        circuit.width(left.size)
        val result = IntArray(left.size)
        var carry = carryIn
        for (index in result.indices) {
            tick()
            val different = circuit.xor(left[index], right[index])
            result[index] = circuit.xor(different, carry)
            if (index != result.lastIndex) {
                carry = circuit.or(circuit.and(left[index], right[index]), circuit.and(different, carry))
            }
        }
        return result
    }

    suspend fun not(bits: IntArray): IntArray {
        circuit.width(bits.size)
        val result = IntArray(bits.size)
        for (index in result.indices) {
            tick()
            result[index] = -bits[index]
        }
        return result
    }

    suspend fun negate(bits: IntArray): IntArray = add(not(bits), IntArray(bits.size) { circuit.falseLiteral }, circuit.trueLiteral)

    suspend fun subtract(
        left: IntArray,
        right: IntArray,
    ): IntArray = add(left, negate(right))

    suspend fun multiply(
        left: IntArray,
        right: IntArray,
        width: Int,
        signed: Boolean,
    ): IntArray {
        circuit.width(width)
        require(left.isNotEmpty() && right.isNotEmpty())
        val multiplicand = if (left.size >= right.size) left else right
        val multiplier = if (left.size >= right.size) right else left
        var result = IntArray(width) { circuit.falseLiteral }
        for (shift in 0 until minOf(multiplier.size, width)) {
            tick()
            val selected = multiplier[shift]
            if (selected == circuit.falseLiteral) continue
            val partial = IntArray(width) { circuit.falseLiteral }
            for (index in shift until width) {
                tick()
                partial[index] = circuit.and(selected, bit(multiplicand, index - shift, signed))
            }
            result = if (signed && shift == multiplier.lastIndex) subtract(result, partial) else add(result, partial)
        }
        return result
    }

    suspend fun equal(
        left: IntArray,
        right: IntArray,
        signed: Boolean,
    ): Int {
        val width = maxOf(left.size, right.size)
        circuit.width(width)
        require(left.isNotEmpty() && right.isNotEmpty())
        var result = circuit.trueLiteral
        for (index in 0 until width) {
            tick()
            result = circuit.and(result, -circuit.xor(bit(left, index, signed), bit(right, index, signed)))
        }
        return result
    }

    suspend fun less(
        left: IntArray,
        right: IntArray,
        signed: Boolean,
    ): Int {
        val width = maxOf(left.size, right.size)
        circuit.width(width)
        require(left.isNotEmpty() && right.isNotEmpty())
        var result = circuit.falseLiteral
        for (index in 0 until width) {
            tick()
            val leftBit = bit(left, index, signed)
            val rightBit = bit(right, index, signed)
            result = circuit.choose(circuit.xor(leftBit, rightBit), rightBit, result)
        }
        return if (signed) circuit.choose(circuit.xor(left.last(), right.last()), left.last(), result) else result
    }

    suspend fun choose(
        condition: Int,
        whenTrue: IntArray,
        whenFalse: IntArray,
        width: Int,
    ): IntArray {
        circuit.width(width)
        require(whenTrue.isNotEmpty() && whenFalse.isNotEmpty())
        val result = IntArray(width)
        for (index in result.indices) {
            tick()
            result[index] = circuit.choose(condition, bit(whenTrue, index, signed = true), bit(whenFalse, index, signed = true))
        }
        return result
    }

    suspend fun bitwise(
        operator: BitVecOperator,
        left: IntArray,
        right: IntArray,
    ): IntArray {
        require(left.size == right.size)
        circuit.width(left.size)
        val result = IntArray(left.size)
        for (index in result.indices) {
            tick()
            result[index] =
                when (operator) {
                    BitVecOperator.AND -> circuit.and(left[index], right[index])
                    BitVecOperator.OR -> circuit.or(left[index], right[index])
                    BitVecOperator.XOR -> circuit.xor(left[index], right[index])
                    else -> error("Not a bitwise operator: $operator")
                }
        }
        return result
    }

    suspend fun shift(
        operator: BitVecOperator,
        bits: IntArray,
        amount: IntArray,
    ): IntArray {
        circuit.width(bits.size)
        require(amount.isNotEmpty())
        val fill = if (operator == BitVecOperator.ARITHMETIC_SHIFT_RIGHT) bits.last() else circuit.falseLiteral
        var result = bits.copyOf()
        var index = 0
        var distance = 1L
        while (index < amount.size && distance < bits.size.toLong()) {
            val shifted = IntArray(bits.size)
            for (position in shifted.indices) {
                tick()
                val source = if (operator == BitVecOperator.SHIFT_LEFT) position.toLong() - distance else position.toLong() + distance
                val shiftedBit = if (source >= 0 && source < bits.size.toLong()) result[source.toInt()] else fill
                shifted[position] = circuit.choose(amount[index], shiftedBit, result[position])
            }
            result = shifted
            distance *= 2
            index++
        }
        var oversized = circuit.falseLiteral
        while (index < amount.size) {
            tick()
            oversized = circuit.or(oversized, amount[index++])
        }
        for (position in result.indices) {
            tick()
            result[position] = circuit.choose(oversized, fill, result[position])
        }
        return result
    }

    private fun bit(
        bits: IntArray,
        index: Int,
        signed: Boolean,
    ): Int =
        if (index < bits.size) {
            bits[index]
        } else if (signed) {
            bits.last()
        } else {
            circuit.falseLiteral
        }

    private suspend fun tick() {
        if (operations++ and 127 == 0) checkpoint()
    }
}
