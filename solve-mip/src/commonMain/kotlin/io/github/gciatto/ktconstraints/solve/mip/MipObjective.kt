package io.github.gciatto.ktconstraints.solve.mip

import org.gciatto.kt.math.BigInteger

/** An affine objective `Σ coefficient · variable + constant` over variable indices. */
internal data class MipObjective(
    val terms: Map<Int, BigInteger>,
    val constant: BigInteger,
    val maximize: Boolean,
)
