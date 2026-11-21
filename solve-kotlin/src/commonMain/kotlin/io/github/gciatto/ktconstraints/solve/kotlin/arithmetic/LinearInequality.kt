package io.github.gciatto.ktconstraints.solve.kotlin.arithmetic

import io.github.gciatto.ktconstraints.core.Rational

/** Exact row `sum(coefficients[i] * x[i]) <= upperBound`, or `<` when [strict]. */
internal class LinearInequality(
    coefficients: Map<Int, Rational>,
    val upperBound: Rational,
    val strict: Boolean = false,
) {
    /** Immutable, ordered nonzero coefficients; variable indices are zero-based. */
    val coefficients: Map<Int, Rational> =
        buildMap {
            for ((variable, coefficient) in coefficients.entries.sortedBy { it.key }) {
                if (coefficient != Rational.ZERO) put(variable, coefficient)
            }
        }
}
