package io.github.gciatto.ktconstraints.core.bitvector
import io.github.gciatto.ktconstraints.core.Variable
import org.gciatto.kt.math.BigInteger

/** Exact unsigned bit-vector literal; values outside `[0, 2^width)` are rejected, never truncated. */
data class BitVecLiteral(
    /** Unsigned value. */
    val value: BigInteger,
    override val width: Int,
) : BitVecExpr {
    /** Creates a literal from a machine integer, with the same range check. */
    constructor(value: Long, width: Int) : this(BigInteger.of(value), width)

    init {
        require(width > 0) { "Bit-vector width must be positive" }
        require(value.signum >= 0 && value.bitLength <= width) { "Value $value does not fit in $width unsigned bits" }
    }

    override val variables: Set<Variable> get() = emptySet()

    override fun toString(): String = "#$value[$width]"
}
