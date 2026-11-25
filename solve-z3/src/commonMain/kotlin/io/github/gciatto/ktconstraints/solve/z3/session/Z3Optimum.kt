package io.github.gciatto.ktconstraints.solve.z3.session

import io.github.gciatto.ktconstraints.core.Rational

/**
 * Outcome of one Z3 optimization. When satisfiable, the optimum is `infinity·∞ + value + epsilon·ε`: a non-zero
 * [infinity] means the objective is unbounded, and a non-zero [epsilon] means [value] is a supremum or infimum that no
 * solution attains.
 */
internal data class Z3Optimum(
    val outcome: Z3Outcome,
    val infinity: Rational = Rational.ZERO,
    val value: Rational = Rational.ZERO,
    val epsilon: Rational = Rational.ZERO,
)
