package io.github.gciatto.ktconstraints.solve.kotlin.compiler.linear
import io.github.gciatto.ktconstraints.solve.kotlin.compiler.circuit.BinaryArithmetic
import io.github.gciatto.ktconstraints.solve.kotlin.compiler.circuit.Circuit
import io.github.gciatto.ktconstraints.solve.kotlin.compiler.encoding.DirectEncoding
import io.github.gciatto.ktconstraints.solve.kotlin.compiler.encoding.IntegerEncoding
import org.gciatto.kt.math.BigInteger

/**
 * Compares integer terms through reified native linear constraints over their bits instead of adder circuits, and
 * materialises symbolic sums for consumers that need bits. Sums whose coefficients exceed machine range fall back to
 * the exact circuits.
 */
internal class LinearCompiler(
    private val circuit: Circuit,
    private val binary: BinaryArithmetic,
    private val direct: DirectEncoding,
) {
    private val materialized = mutableMapOf<IntegerLinear, IntegerEncoding>()

    /** Bits of [value], an [IntegerEncoding] or an [IntegerLinear] summed by the circuit it always had. */
    suspend fun encoding(value: Any): IntegerEncoding =
        when (value) {
            is IntegerEncoding -> value
            is IntegerLinear ->
                materialized.getOrPut(value) {
                    value.parts.fold(binary.integer(value.constant)) { sum, (encoding, coefficient) ->
                        binary.add(sum, binary.multiply(encoding, binary.integer(coefficient)))
                    }
                }
            else -> error("Not an integer term")
        }

    // Native constraints only pay off for symbolic sums; two encodings keep their comparator circuits.
    suspend fun lessOrEqual(
        left: Any,
        right: Any,
    ): Int =
        native(left, right) { atMostZero(linear(left) + linear(right).scale(-BigInteger.ONE)) }
            ?: -binary.less(encoding(right), encoding(left))

    suspend fun less(
        left: Any,
        right: Any,
    ): Int =
        native(left, right) { atMostZero(linear(left) + linear(right).scale(-BigInteger.ONE) + IntegerLinear(emptyList(), BigInteger.ONE)) }
            ?: binary.less(encoding(left), encoding(right))

    suspend fun equal(
        left: Any,
        right: Any,
    ): Int =
        if (left is IntegerLinear || right is IntegerLinear) {
            circuit.and(lessOrEqual(left, right), lessOrEqual(right, left))
        } else {
            equal(encoding(left), encoding(right))
        }

    /** Equality of encodings, through a value literal when one side is a constant and the other has them. */
    suspend fun equal(
        left: IntegerEncoding,
        right: IntegerEncoding,
    ): Int =
        (if (right.min == right.max) direct.equal(left, right.min) else null)
            ?: (if (left.min == left.max) direct.equal(right, left.min) else null)
            ?: binary.equal(left, right)

    private suspend fun native(
        left: Any,
        right: Any,
        form: suspend () -> Int?,
    ): Int? = if (left is IntegerLinear || right is IntegerLinear) form() else null

    private fun linear(value: Any): IntegerLinear = value as? IntegerLinear ?: IntegerLinear.of(value as IntegerEncoding)

    /** A literal equivalent to `form <= 0`, or null when its coefficients do not fit machine range. */
    private suspend fun atMostZero(form: IntegerLinear): Int? {
        if (form.max.signum <= 0) return circuit.trueLiteral
        if (form.min.signum > 0) return circuit.falseLiteral
        val weights = mutableMapOf<Int, BigInteger>()
        var constant = form.constant
        for ((encoding, coefficient) in form.parts) {
            circuit.checkpoint()
            constant += coefficient * weigh(encoding, weights, coefficient)
        }
        // Positive coefficients only: c * x = c + |c| * (not x) for negative c.
        var bound = -constant
        val literals = mutableListOf<Int>()
        val coefficients = mutableListOf<BigInteger>()
        for ((variable, weight) in weights) {
            if (weight.signum == 0) continue
            if (weight.signum > 0) {
                literals += variable
                coefficients += weight
            } else {
                literals += -variable
                coefficients += -weight
                bound -= weight
            }
        }
        val total = coefficients.fold(BigInteger.ZERO, BigInteger::plus)
        if (bound.signum < 0) return circuit.falseLiteral
        if (total <= bound) return circuit.trueLiteral
        if (total.bitLength > MAX_BITS) return null
        return reify(literals, coefficients.map { it.toLong() }, bound.toLong(), total.toLong())
    }

    /** Adds `coefficient * encoding`'s literal weights to [weights] and returns the encoding's constant part. */
    private fun weigh(
        encoding: IntegerEncoding,
        weights: MutableMap<Int, BigInteger>,
        coefficient: BigInteger,
    ): BigInteger {
        val offset = encoding.offset
        val bits = offset ?: encoding.bits
        var constant = if (offset != null) encoding.min else BigInteger.ZERO
        for (index in bits.indices) {
            val magnitude = BigInteger.ONE shl index
            val weight = if (offset == null && index == bits.lastIndex) -magnitude else magnitude
            when (val literal = bits[index]) {
                circuit.trueLiteral -> constant += weight
                circuit.falseLiteral -> Unit
                else -> {
                    val variable = if (literal > 0) literal else -literal
                    // A negative literal contributes weight * (1 - variable).
                    if (literal < 0) constant += weight
                    val signed = if (literal > 0) weight else -weight
                    weights[variable] = (weights[variable] ?: BigInteger.ZERO) + signed * coefficient
                }
            }
        }
        return constant
    }

    /** A fresh literal `r` with `r -> sum <= bound` and `not r -> sum >= bound + 1`. */
    private fun reify(
        literals: List<Int>,
        coefficients: List<Long>,
        bound: Long,
        total: Long,
    ): Int {
        val reified = circuit.variable()
        circuit.linear((literals + reified).toIntArray(), (coefficients + (total - bound)).toLongArray(), total)
        circuit.linear((literals.map { -it } + -reified).toIntArray(), (coefficients + (bound + 1)).toLongArray(), total)
        return reified
    }

    private companion object {
        /** Keeps every reified coefficient sum, at most about twice the total, inside Long. */
        const val MAX_BITS = 60
    }
}
