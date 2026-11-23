package io.github.gciatto.ktconstraints.solve.minizinc.lowering
import io.github.gciatto.ktconstraints.core.domain.IntInterval

internal data class LoweredExpression(
    val text: String,
    val bounds: IntInterval? = null,
)
