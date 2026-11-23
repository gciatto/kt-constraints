package io.github.gciatto.ktconstraints.solve.mip.program
import org.gciatto.kt.math.BigInteger

/** An integer program variable with exact inclusive bounds; Booleans are `0..1`. */
internal data class MipVariable(
    val min: BigInteger,
    val max: BigInteger,
)
