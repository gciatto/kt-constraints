package io.github.gciatto.ktconstraints.solve.auto.examples.howto

import org.gciatto.kt.math.BigInteger

/** The optimum of `2x + y` at each step of [reoptimize]. */
internal data class Reoptimized(
    val initial: BigInteger?,
    val inFrame: BigInteger?,
    val assumed: BigInteger?,
    val restored: BigInteger?,
)
