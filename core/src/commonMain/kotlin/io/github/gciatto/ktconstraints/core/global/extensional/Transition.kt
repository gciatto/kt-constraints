package io.github.gciatto.ktconstraints.core.global.extensional
import org.gciatto.kt.math.BigInteger

/** An automaton edge: reading [symbol] in state [from] moves to state [to]. */
data class Transition(
    /** Source state, non-negative. */
    val from: Int,
    /** Symbol read. */
    val symbol: BigInteger,
    /** Target state, non-negative. */
    val to: Int,
)
