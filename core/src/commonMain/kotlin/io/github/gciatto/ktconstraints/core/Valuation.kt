package io.github.gciatto.ktconstraints.core

import org.gciatto.kt.math.BigInteger

/** Solver-independent typed variable bindings for pure expression evaluation. */
interface Valuation {
    /** Returns a Boolean binding, or null when absent. */
    fun valueOf(variable: BoolVar): Boolean?

    /** Returns an exact integer binding, or null when absent. */
    fun valueOf(variable: IntVar): BigInteger?
}
