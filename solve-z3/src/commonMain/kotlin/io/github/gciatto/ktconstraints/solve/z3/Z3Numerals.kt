package io.github.gciatto.ktconstraints.solve.z3

import io.github.gciatto.ktconstraints.core.Rational
import io.github.gciatto.ktconstraints.solve.spi.BackendExecutionException

/** Reads a Z3 real numeral (`p` or `p/q`); anything else, such as an algebraic number, fails instead of being rounded. */
internal fun exactRational(numeral: String?): Rational =
    try {
        Rational.parse(requireNotNull(numeral) { "Z3 returned no real numeral" })
    } catch (problem: IllegalArgumentException) {
        throw BackendExecutionException("Z3 returned a real value that is not an exact rational: $numeral", problem)
    }
