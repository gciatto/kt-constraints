package io.github.gciatto.ktconstraints.solve.kotlin.arithmetic

import io.github.gciatto.ktconstraints.core.Rational

/**
 * A complete rational witness, or null only after exact infeasibility has been established; then [conflict] holds
 * the indices of the inequalities whose combination is contradictory.
 */
internal class LinearResult(
    values: List<Rational>?,
    val conflict: List<Int> = emptyList(),
) {
    val values: List<Rational>? = values?.let { source -> buildList { addAll(source) } }
}
