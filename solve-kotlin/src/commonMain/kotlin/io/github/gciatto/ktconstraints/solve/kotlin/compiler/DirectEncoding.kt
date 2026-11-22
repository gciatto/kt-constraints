package io.github.gciatto.ktconstraints.solve.kotlin.compiler

import io.github.gciatto.ktconstraints.core.IntDomain
import org.gciatto.kt.math.BigInteger

/**
 * Value literals `x = v` for integer variables with small domains, created on first use. Exactly one holds, and each
 * fixes the variable's offset bits, so they channel both ways with the binary encoding through unit propagation.
 */
internal class DirectEncoding(
    private val circuit: Circuit,
) {
    private val domains = mutableMapOf<IntegerEncoding, IntDomain>()
    private val literals = mutableMapOf<IntegerEncoding, Map<BigInteger, Int>>()

    /** Makes [encoding], a variable over [domain], eligible when the domain has at most [MAX_VALUES] values. */
    fun register(
        encoding: IntegerEncoding,
        domain: IntDomain,
    ) {
        if (encoding.offset != null && domain.cardinality <= BigInteger.of(MAX_VALUES)) domains[encoding] = domain
    }

    fun isDirect(encoding: IntegerEncoding): Boolean = encoding in domains

    /** The literal of `encoding = value`, or null when [encoding] has no value literals. */
    fun equal(
        encoding: IntegerEncoding,
        value: BigInteger,
    ): Int? {
        if (encoding !in domains) return null
        return values(encoding)[value] ?: circuit.falseLiteral
    }

    /** Every value of [encoding]'s domain with its literal. */
    fun values(encoding: IntegerEncoding): Map<BigInteger, Int> =
        literals.getOrPut(encoding) {
            val domain = domains.getValue(encoding)
            val offset = requireNotNull(encoding.offset)
            val result = linkedMapOf<BigInteger, Int>()
            for (interval in domain.intervals) {
                var value = interval.min
                while (value <= interval.max) {
                    val literal = circuit.variable()
                    val bits = value - encoding.min
                    for (index in offset.indices) {
                        val set = (bits shr index).rem(BigInteger.of(2)).signum != 0
                        circuit.clause(-literal, if (set) offset[index] else -offset[index])
                    }
                    result[value] = literal
                    value += BigInteger.ONE
                }
            }
            circuit.clause(*result.values.toIntArray())
            circuit.linear(result.values.toIntArray(), LongArray(result.size) { 1L }, 1L)
            result
        }

    /**
     * Posts a top-level all-different over `encoding + shift` operands (a null encoding is the constant `shift`):
     * at most one operand per value, and every value taken when there are exactly as many values as operands.
     */
    fun allDifferent(operands: List<Pair<IntegerEncoding?, BigInteger>>) {
        val byValue = linkedMapOf<BigInteger, MutableList<Int>>()
        val constants = mutableMapOf<BigInteger, Int>()
        for ((encoding, shift) in operands) {
            if (encoding == null) {
                constants[shift] = (constants[shift] ?: 0) + 1
                byValue.getOrPut(shift) { mutableListOf() }
            } else {
                values(encoding).forEach { (value, literal) -> byValue.getOrPut(value + shift) { mutableListOf() } += literal }
            }
        }
        for ((value, candidates) in byValue) {
            when (constants[value] ?: 0) {
                0 -> if (candidates.size > 1) circuit.linear(candidates.toIntArray(), LongArray(candidates.size) { 1L }, 1L)
                1 -> candidates.forEach { circuit.clause(-it) }
                else -> circuit.clause()
            }
        }
        // Hall's condition: fewer candidate values than operands is a contradiction, not a search.
        if (byValue.size < operands.size) circuit.clause()
        if (byValue.size == operands.size) {
            byValue.forEach { (value, candidates) -> if (value !in constants) circuit.clause(*candidates.toIntArray()) }
        }
    }

    /** Posts `left.first + left.second != right.first + right.second` as one binary clause per clashing value pair. */
    fun notEqual(
        left: Pair<IntegerEncoding?, BigInteger>,
        right: Pair<IntegerEncoding?, BigInteger>,
    ) {
        val (first, firstShift) = left
        val (second, secondShift) = right
        when {
            first == null && second == null -> if (firstShift == secondShift) circuit.clause()
            first == null -> values(requireNotNull(second))[firstShift - secondShift]?.let { circuit.clause(-it) }
            second == null -> values(first)[secondShift - firstShift]?.let { circuit.clause(-it) }
            else -> {
                val partners = values(second)
                for ((value, literal) in values(first)) {
                    partners[value + firstShift - secondShift]?.let { circuit.clause(-literal, -it) }
                }
            }
        }
    }

    private companion object {
        const val MAX_VALUES = 128L
    }
}
