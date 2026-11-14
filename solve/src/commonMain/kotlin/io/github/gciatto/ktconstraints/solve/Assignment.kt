package io.github.gciatto.ktconstraints.solve

import io.github.gciatto.ktconstraints.core.BoolVar
import io.github.gciatto.ktconstraints.core.IntVar

/** Read-only projection of one solver assignment. */
interface Assignment {
    /** Returns null when the variable is not assigned or not part of the projection. */
    fun valueOf(variable: BoolVar): Boolean?

    /** Initially limited to [Long] domains; arbitrary-precision integer results are on the roadmap. */
    fun valueOf(variable: IntVar): Long?
}
