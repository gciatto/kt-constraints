package io.github.gciatto.ktconstraints.solve

import org.gciatto.kt.math.BigInteger

/** A projected satisfaction solution or an improving optimization incumbent. */
data class SolutionFound(
    /** Feasible values for the requested output projection. */
    val assignment: Assignment,
    /** Exact objective value for an optimization incumbent, otherwise null. */
    val objectiveValue: BigInteger? = null,
) : SolutionEvent
