package io.github.gciatto.ktconstraints.solve.kotlin.compiler.encoding
import org.gciatto.kt.math.BigInteger

/**
 * Little-endian, signed two's-complement literals with conservative exact mathematical bounds. A variable also keeps
 * its unsigned [offset] bits, whose value plus [min] it equals, so linear constraints can skip the origin adder.
 */
internal class IntegerEncoding(
    bits: IntArray,
    val min: BigInteger,
    val max: BigInteger,
    offset: IntArray? = null,
) {
    val bits: IntArray = bits.copyOf()
    val offset: IntArray? = offset?.copyOf()

    init {
        require(bits.isNotEmpty()) { "A signed encoding needs a sign bit" }
        require(min <= max) { "An encoding must have ordered bounds" }
    }
}
