package io.github.gciatto.ktconstraints.solve.cpsat

import io.github.gciatto.ktconstraints.solve.spi.BoundedIntegerSupport
import org.gciatto.kt.math.BigInteger

private const val LIMIT_BITS = 61

/** The Boolean/integer subset CP-SAT represents exactly within int64; the bound keeps `lhs - rhs + 1` in int64. */
internal object CpSatSupport :
    BoundedIntegerSupport(BigInteger.TWO.pow(LIMIT_BITS), "the CP-SAT int64 margin of 2^61", nestedGlobalsDecompose = true)
