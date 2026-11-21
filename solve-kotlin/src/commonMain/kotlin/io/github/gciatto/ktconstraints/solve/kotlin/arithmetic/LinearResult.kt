package io.github.gciatto.ktconstraints.solve.kotlin.arithmetic

import io.github.gciatto.ktconstraints.core.Rational

/** A complete rational witness, or null only after exact infeasibility has been established. */
internal class LinearResult(values: List<Rational>?) {
    val values: List<Rational>? = values?.let { source -> buildList { addAll(source) } }
}
