package io.github.gciatto.ktconstraints.solve.linear

import io.github.gciatto.ktconstraints.core.Rational

/** An affine objective `Σ terms[i]·x[i] + constant` over column indices. */
data class LinearObjective(
    /** Non-zero coefficients by column index. */
    val terms: Map<Int, Rational>,
    /** Constant offset. */
    val constant: Rational,
    /** Whether larger values are better. */
    val maximize: Boolean,
    /** Whether the model's objective is real, rather than integer. */
    val real: Boolean,
)
