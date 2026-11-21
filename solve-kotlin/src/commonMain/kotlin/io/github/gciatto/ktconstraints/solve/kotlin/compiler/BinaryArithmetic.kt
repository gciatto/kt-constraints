package io.github.gciatto.ktconstraints.solve.kotlin.compiler

import io.github.gciatto.ktconstraints.core.BitVecOperator
import io.github.gciatto.ktconstraints.core.BitVecRelation
import io.github.gciatto.ktconstraints.core.IntDomain
import io.github.gciatto.ktconstraints.solve.TerminationReason
import io.github.gciatto.ktconstraints.solve.kotlin.SearchStopped
import org.gciatto.kt.math.BigInteger

/** Compiles exact finite integers and wrapping bit-vectors into fully reified Boolean circuits. */
internal class BinaryArithmetic(
    private val circuit: Circuit,
    private val checkpoint: suspend () -> Unit = {},
) {
    private val gates = BitCircuit(circuit, checkpoint)

    suspend fun integer(value: BigInteger): IntegerEncoding = IntegerEncoding(signedConstant(value, width(value, value)), value, value)

    /** Encodes an unsigned offset from the lower bound, retaining arbitrary-precision origins and domain holes. */
    suspend fun variable(domain: IntDomain): IntegerEncoding {
        checkpoint()
        if (domain.isEmpty) {
            circuit.clause()
            return integer(BigInteger.ZERO)
        }
        val minimum = requireNotNull(domain.min)
        val maximum = requireNotNull(domain.max)
        if (minimum == maximum) return integer(minimum)
        val resultWidth = width(minimum, maximum)
        val offsetWidth = checkedWidth((maximum - minimum).bitLength.toLong())
        val offset = IntArray(offsetWidth)
        for (index in offset.indices) {
            if (index and 127 == 0) checkpoint()
            offset[index] = circuit.variable()
        }
        var allowed = circuit.falseLiteral
        for (interval in domain.intervals) {
            checkpoint()
            val lower = gates.constant(interval.min - minimum, offsetWidth)
            val upper = gates.constant(interval.max - minimum, offsetWidth)
            val atLeast = -gates.less(offset, lower, signed = false)
            val atMost = -gates.less(upper, offset, signed = false)
            allowed = circuit.or(allowed, circuit.and(atLeast, atMost))
        }
        circuit.clause(allowed)
        val encoded = gates.add(signedConstant(minimum, resultWidth), gates.extend(offset, resultWidth, signed = false))
        return IntegerEncoding(encoded, minimum, maximum)
    }

    suspend fun add(
        left: IntegerEncoding,
        right: IntegerEncoding,
    ): IntegerEncoding {
        if (left.min == left.max && right.min == right.max) return integer(left.min + right.min)
        val minimum = left.min + right.min
        val maximum = left.max + right.max
        val width = width(minimum, maximum)
        return IntegerEncoding(gates.add(gates.extend(left.bits, width, true), gates.extend(right.bits, width, true)), minimum, maximum)
    }

    suspend fun subtract(
        left: IntegerEncoding,
        right: IntegerEncoding,
    ): IntegerEncoding {
        if (left.min == left.max && right.min == right.max) return integer(left.min - right.min)
        val minimum = left.min - right.max
        val maximum = left.max - right.min
        val width = width(minimum, maximum)
        return IntegerEncoding(
            gates.subtract(gates.extend(left.bits, width, true), gates.extend(right.bits, width, true)),
            minimum,
            maximum,
        )
    }

    suspend fun multiply(
        left: IntegerEncoding,
        right: IntegerEncoding,
    ): IntegerEncoding {
        if (left.min == left.max && right.min == right.max) return integer(left.min * right.min)
        if (left.min == BigInteger.ZERO && left.max == BigInteger.ZERO || right.min == BigInteger.ZERO && right.max == BigInteger.ZERO) {
            return integer(BigInteger.ZERO)
        }
        val products = listOf(left.min * right.min, left.min * right.max, left.max * right.min, left.max * right.max)
        val minimum = products.minOrNull()!!
        val maximum = products.maxOrNull()!!
        return IntegerEncoding(gates.multiply(left.bits, right.bits, width(minimum, maximum), signed = true), minimum, maximum)
    }

    suspend fun negate(operand: IntegerEncoding): IntegerEncoding {
        if (operand.min == operand.max) return integer(-operand.min)
        val minimum = -operand.max
        val maximum = -operand.min
        return IntegerEncoding(gates.negate(gates.extend(operand.bits, width(minimum, maximum), signed = true)), minimum, maximum)
    }

    suspend fun equal(
        left: IntegerEncoding,
        right: IntegerEncoding,
    ): Int {
        if (left.max < right.min || right.max < left.min) return circuit.falseLiteral
        return gates.equal(left.bits, right.bits, signed = true)
    }

    suspend fun less(
        left: IntegerEncoding,
        right: IntegerEncoding,
    ): Int {
        if (left.max < right.min) return circuit.trueLiteral
        if (left.min >= right.max) return circuit.falseLiteral
        return gates.less(left.bits, right.bits, signed = true)
    }

    suspend fun choose(
        condition: Int,
        whenTrue: IntegerEncoding,
        whenFalse: IntegerEncoding,
    ): IntegerEncoding {
        if (condition == circuit.trueLiteral) return whenTrue
        if (condition == circuit.falseLiteral) return whenFalse
        val minimum = minOf(whenTrue.min, whenFalse.min)
        val maximum = maxOf(whenTrue.max, whenFalse.max)
        return IntegerEncoding(gates.choose(condition, whenTrue.bits, whenFalse.bits, width(minimum, maximum)), minimum, maximum)
    }

    suspend fun bits(
        value: BigInteger,
        width: Int,
    ): IntArray = gates.constant(value, width)

    suspend fun bitVector(
        operator: BitVecOperator,
        left: IntArray,
        right: IntArray,
    ): IntArray {
        require(left.size == right.size && left.isNotEmpty()) { "Bit-vector operands must have the same positive width" }
        circuit.width(left.size)
        return when (operator) {
            BitVecOperator.ADD -> gates.add(left, right)
            BitVecOperator.SUBTRACT -> gates.subtract(left, right)
            BitVecOperator.MULTIPLY -> gates.multiply(left, right, left.size, signed = false)
            BitVecOperator.AND, BitVecOperator.OR, BitVecOperator.XOR -> gates.bitwise(operator, left, right)
            BitVecOperator.SHIFT_LEFT, BitVecOperator.LOGICAL_SHIFT_RIGHT, BitVecOperator.ARITHMETIC_SHIFT_RIGHT ->
                gates.shift(
                    operator,
                    left,
                    right,
                )
        }
    }

    suspend fun bitNot(operand: IntArray): IntArray = gates.not(operand)

    suspend fun bitNegate(operand: IntArray): IntArray = gates.negate(operand)

    suspend fun bitCompare(
        relation: BitVecRelation,
        left: IntArray,
        right: IntArray,
    ): Int {
        require(left.size == right.size && left.isNotEmpty()) { "Bit-vector operands must have the same positive width" }
        return when (relation) {
            BitVecRelation.EQUAL -> gates.equal(left, right, signed = false)
            BitVecRelation.UNSIGNED_LESS_THAN -> gates.less(left, right, signed = false)
            BitVecRelation.UNSIGNED_LESS_OR_EQUAL -> -gates.less(right, left, signed = false)
            BitVecRelation.SIGNED_LESS_THAN -> gates.less(left, right, signed = true)
            BitVecRelation.SIGNED_LESS_OR_EQUAL -> -gates.less(right, left, signed = true)
        }
    }

    suspend fun toInteger(
        bits: IntArray,
        signed: Boolean,
    ): IntegerEncoding {
        circuit.width(bits.size)
        if (signed) {
            val magnitude = BigInteger.ONE shl (bits.size - 1)
            return IntegerEncoding(bits, -magnitude, magnitude - BigInteger.ONE)
        }
        val width = checkedWidth(bits.size.toLong() + 1)
        return IntegerEncoding(gates.extend(bits, width, signed = false), BigInteger.ZERO, (BigInteger.ONE shl bits.size) - BigInteger.ONE)
    }

    fun decodeInteger(
        encoding: IntegerEncoding,
        values: BooleanArray,
    ): BigInteger {
        val unsigned = decodeBits(encoding.bits, values)
        return if (circuit.value(encoding.bits.last(), values)) unsigned - (BigInteger.ONE shl encoding.bits.size) else unsigned
    }

    fun decodeBits(
        bits: IntArray,
        values: BooleanArray,
    ): BigInteger {
        var result = BigInteger.ZERO
        for (index in bits.indices.reversed()) {
            result = result shl 1
            if (circuit.value(bits[index], values)) result += BigInteger.ONE
        }
        return result
    }

    private suspend fun signedConstant(
        value: BigInteger,
        width: Int,
    ): IntArray = gates.constant(if (value.signum < 0) value + (BigInteger.ONE shl width) else value, width)

    private fun width(
        minimum: BigInteger,
        maximum: BigInteger,
    ): Int {
        val negativeMagnitude = if (minimum.signum < 0) (-minimum - BigInteger.ONE).bitLength else minimum.bitLength
        val positiveMagnitude = if (maximum.signum < 0) (-maximum - BigInteger.ONE).bitLength else maximum.bitLength
        return checkedWidth(maxOf(negativeMagnitude, positiveMagnitude).toLong() + 1)
    }

    private fun checkedWidth(width: Long): Int {
        if (width > Int.MAX_VALUE) throw SearchStopped(TerminationReason.RESOURCE_LIMIT)
        return width.toInt().also(circuit::width)
    }
}
