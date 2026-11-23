package io.github.gciatto.ktconstraints.solve.spi.managed
import io.github.gciatto.ktconstraints.solve.result.SolveResult
import io.github.gciatto.ktconstraints.solve.stream.SolutionFound

internal data class StreamState(
    val incumbent: SolutionFound? = null,
    val terminal: SolveResult? = null,
    val delivered: Boolean = false,
)
