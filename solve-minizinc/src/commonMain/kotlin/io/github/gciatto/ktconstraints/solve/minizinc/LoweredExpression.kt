package io.github.gciatto.ktconstraints.solve.minizinc

import io.github.gciatto.ktconstraints.core.IntInterval

internal data class LoweredExpression(
    val text: String,
    val bounds: IntInterval? = null,
)
