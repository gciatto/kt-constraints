package io.github.gciatto.ktconstraints.solve.kotlin.arithmetic

import io.github.gciatto.ktconstraints.core.Rational

/** One exact endpoint; an open endpoint must not be selected as a witness. */
internal class LinearBound(
    val value: Rational,
    val strict: Boolean,
)
