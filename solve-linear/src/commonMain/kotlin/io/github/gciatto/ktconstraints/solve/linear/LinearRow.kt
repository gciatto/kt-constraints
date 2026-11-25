package io.github.gciatto.ktconstraints.solve.linear

import io.github.gciatto.ktconstraints.core.Rational

/** A linear constraint `Σ terms[i]·x[i] relation rhs` over column indices, with exact coefficients. */
data class LinearRow(
    /** Non-zero coefficients by column index. */
    val terms: Map<Int, Rational>,
    /** The comparison with [rhs]. */
    val relation: LinearRelation,
    /** Right-hand side. */
    val rhs: Rational,
)
