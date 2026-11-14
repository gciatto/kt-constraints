package io.github.gciatto.ktconstraints.solve

import io.github.gciatto.ktconstraints.core.BoolVar
import io.github.gciatto.ktconstraints.core.IntVar
import org.gciatto.kt.math.BigInteger

/** Read-only projection of one solver assignment. */
interface Assignment {
    /** Returns null when the variable is not assigned or not part of the projection. */
    fun valueOf(variable: BoolVar): Boolean?

    /** Returns the exact integer value, or null when outside the assignment projection. */
    fun valueOf(variable: IntVar): BigInteger?
}
