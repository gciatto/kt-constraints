package io.github.gciatto.ktconstraints.solve.mip

import org.gciatto.kt.math.BigInteger

/** A linear row `min ≤ Σ coefficient · variable ≤ max` over variable indices; a null side is unbounded. */
internal data class MipRow(
    val terms: Map<Int, BigInteger>,
    val min: BigInteger?,
    val max: BigInteger?,
)
