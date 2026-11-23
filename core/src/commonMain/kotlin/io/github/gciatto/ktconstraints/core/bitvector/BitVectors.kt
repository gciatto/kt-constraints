package io.github.gciatto.ktconstraints.core.bitvector
import io.github.gciatto.ktconstraints.core.bitvector.operation.BitVecBinary
import io.github.gciatto.ktconstraints.core.bitvector.operation.BitVecComparison
import io.github.gciatto.ktconstraints.core.bitvector.operation.BitVecNegation
import io.github.gciatto.ktconstraints.core.bitvector.operation.BitVecNot
import io.github.gciatto.ktconstraints.core.bitvector.operation.BitVecOperator
import io.github.gciatto.ktconstraints.core.bitvector.operation.BitVecRelation
import org.gciatto.kt.math.BigInteger

/** SMT-LIB `QF_BV` arithmetic on unsigned values, shared by evaluation and assignment validation. */
internal object BitVectors {
    fun fits(
        value: BigInteger,
        width: Int,
    ): Boolean = value.signum >= 0 && value.bitLength <= width

    fun toInt(
        value: BigInteger,
        width: Int,
        signed: Boolean,
    ): BigInteger = if (signed && value.testBit(width - 1)) value - modulus(width) else value

    fun evaluate(
        node: BitVecExpr,
        values: List<Any>,
    ): BigInteger {
        val width = node.width
        val modulus = modulus(width)
        return when (node) {
            is BitVecLiteral -> node.value
            is BitVecNot -> modulus - BigInteger.ONE - (values[0] as BigInteger)
            is BitVecNegation -> wrap(-(values[0] as BigInteger), modulus)
            is BitVecBinary -> binary(node.operator, values[0] as BigInteger, values[1] as BigInteger, width, modulus)
            else -> throw IllegalArgumentException("Unsupported bit-vector expression: $node")
        }
    }

    fun compare(
        node: BitVecComparison,
        left: BigInteger,
        right: BigInteger,
    ): Boolean {
        val width = node.left.width
        return when (node.relation) {
            BitVecRelation.EQUAL -> left == right
            BitVecRelation.UNSIGNED_LESS_THAN -> left < right
            BitVecRelation.UNSIGNED_LESS_OR_EQUAL -> left <= right
            BitVecRelation.SIGNED_LESS_THAN -> toInt(left, width, true) < toInt(right, width, true)
            BitVecRelation.SIGNED_LESS_OR_EQUAL -> toInt(left, width, true) <= toInt(right, width, true)
        }
    }

    private fun binary(
        operator: BitVecOperator,
        left: BigInteger,
        right: BigInteger,
        width: Int,
        modulus: BigInteger,
    ): BigInteger {
        // Shift amounts are unsigned bit-vectors and may exceed Int; any amount >= width saturates.
        val amount = if (right < BigInteger.of(width)) right.toIntExact() else width
        return when (operator) {
            BitVecOperator.ADD -> wrap(left + right, modulus)
            BitVecOperator.SUBTRACT -> wrap(left - right, modulus)
            BitVecOperator.MULTIPLY -> wrap(left * right, modulus)
            BitVecOperator.AND -> left.and(right)
            BitVecOperator.OR -> left.or(right)
            BitVecOperator.XOR -> left.xor(right)
            BitVecOperator.SHIFT_LEFT -> wrap(left shl amount, modulus)
            BitVecOperator.LOGICAL_SHIFT_RIGHT -> left shr amount
            BitVecOperator.ARITHMETIC_SHIFT_RIGHT -> wrap(toInt(left, width, true) shr amount, modulus)
        }
    }

    private fun modulus(width: Int): BigInteger = BigInteger.ONE shl width

    private fun wrap(
        value: BigInteger,
        modulus: BigInteger,
    ): BigInteger = value.rem(modulus).let { if (it.signum < 0) it + modulus else it }
}
