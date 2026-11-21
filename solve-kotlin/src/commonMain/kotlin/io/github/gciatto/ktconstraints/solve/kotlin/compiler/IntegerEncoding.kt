package io.github.gciatto.ktconstraints.solve.kotlin.compiler

import org.gciatto.kt.math.BigInteger

/** Little-endian, signed two's-complement literals with conservative exact mathematical bounds. */
internal class IntegerEncoding(
    bits: IntArray,
    val min: BigInteger,
    val max: BigInteger,
) {
    val bits: IntArray = bits.copyOf()

    init {
        require(bits.isNotEmpty()) { "A signed encoding needs a sign bit" }
        require(min <= max) { "An encoding must have ordered bounds" }
    }
}
