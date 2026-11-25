package io.github.gciatto.ktconstraints.solve.linear

import io.github.gciatto.ktconstraints.core.Rational
import io.github.gciatto.ktconstraints.core.Variable

/**
 * One decision column: the model [variable] it stands for, whether it is [integer], and its inclusive bounds, where
 * null means unbounded. Booleans are integer columns over `0..1`; an empty integer domain has [lower] above [upper].
 */
data class LinearColumn(
    /** The model variable. */
    val variable: Variable,
    /** Whether values must be integers. */
    val integer: Boolean,
    /** Inclusive lower bound, or null for none. */
    val lower: Rational?,
    /** Inclusive upper bound, or null for none. */
    val upper: Rational?,
)
