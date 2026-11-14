package io.github.gciatto.ktconstraints.solve.spi

import io.github.gciatto.ktconstraints.solve.SolutionFound
import io.github.gciatto.ktconstraints.solve.SolveResult

internal data class StreamState(
    val incumbent: SolutionFound? = null,
    val terminal: SolveResult? = null,
    val delivered: Boolean = false,
)
