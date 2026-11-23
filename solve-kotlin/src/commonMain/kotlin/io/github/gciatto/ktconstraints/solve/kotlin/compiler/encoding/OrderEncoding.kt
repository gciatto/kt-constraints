package io.github.gciatto.ktconstraints.solve.kotlin.compiler.encoding
import io.github.gciatto.ktconstraints.core.domain.IntDomain
import io.github.gciatto.ktconstraints.solve.kotlin.compiler.circuit.BinaryArithmetic
import io.github.gciatto.ktconstraints.solve.kotlin.compiler.circuit.Circuit
import org.gciatto.kt.math.BigInteger

/**
 * Order literals `x >= v` for integer variables with medium domains, created on first use from exact comparators and
 * chained downwards. Inequalities between variables shifted by constants then become one clause per domain value,
 * so bounds propagate through unit propagation instead of comparator circuits.
 */
internal class OrderEncoding(
    private val circuit: Circuit,
    private val binary: BinaryArithmetic,
) {
    private val domains = mutableMapOf<IntegerEncoding, List<BigInteger>>()
    private val literals = mutableMapOf<IntegerEncoding, IntArray>()

    /** Makes [encoding], a variable over [domain], eligible when the domain has 2 to [MAX_VALUES] values. */
    fun register(
        encoding: IntegerEncoding,
        domain: IntDomain,
    ) {
        if (encoding.offset == null || domain.cardinality > BigInteger.of(MAX_VALUES)) return
        domains[encoding] =
            buildList {
                for (interval in domain.intervals) {
                    var value = interval.min
                    while (value <= interval.max) {
                        add(value)
                        value += BigInteger.ONE
                    }
                }
            }
    }

    fun isOrdered(encoding: IntegerEncoding): Boolean = encoding in domains

    /** A literal equivalent to `left.first + left.second <= right.first + right.second`; null encodings are constants. */
    suspend fun lessOrEqual(
        left: Pair<IntegerEncoding?, BigInteger>,
        right: Pair<IntegerEncoding?, BigInteger>,
    ): Int {
        val (first, a) = left
        val (second, b) = right
        return when {
            first == null && second == null -> circuit.constant(a <= b)
            first == null -> atLeast(requireNotNull(second), a - b)
            second == null -> -atLeast(first, b - a + BigInteger.ONE)
            first === second -> circuit.constant(a <= b)
            else -> {
                val holds = circuit.variable()
                // holds: first >= v forces second >= v + a - b; otherwise second >= w forces first >= w + b - a + 1.
                for (value in domains.getValue(first)) clause(-holds, -atLeast(first, value), atLeast(second, value + a - b))
                for (value in domains.getValue(
                    second,
                )) {
                    clause(holds, -atLeast(second, value), atLeast(first, value + b - a + BigInteger.ONE))
                }
                holds
            }
        }
    }

    /** The literal of `encoding >= threshold`. */
    private suspend fun atLeast(
        encoding: IntegerEncoding,
        threshold: BigInteger,
    ): Int {
        val values = domains.getValue(encoding)
        var low = 0
        var high = values.size
        while (low < high) {
            val middle = (low + high) / 2
            if (values[middle] < threshold) low = middle + 1 else high = middle
        }
        return when (low) {
            0 -> circuit.trueLiteral
            values.size -> circuit.falseLiteral
            else -> literals(encoding)[low]
        }
    }

    private suspend fun literals(encoding: IntegerEncoding): IntArray =
        literals.getOrPut(encoding) {
            val values = domains.getValue(encoding)
            val result = IntArray(values.size)
            result[0] = circuit.trueLiteral
            for (index in 1 until values.size) {
                circuit.checkpoint()
                result[index] = -binary.less(encoding, binary.integer(values[index]))
                if (index > 1) circuit.clause(-result[index], result[index - 1])
            }
            result
        }

    private fun clause(vararg literals: Int) {
        if (literals.any { it == circuit.trueLiteral }) return
        circuit.clause(*literals.filter { it != circuit.falseLiteral }.toIntArray())
    }

    private companion object {
        const val MAX_VALUES = 4096L
    }
}
