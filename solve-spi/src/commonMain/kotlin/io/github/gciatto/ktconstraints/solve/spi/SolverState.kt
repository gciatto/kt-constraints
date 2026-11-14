package io.github.gciatto.ktconstraints.solve.spi

internal data class SolverState(
    val closed: Boolean = false,
    val streams: Set<ManagedSolutionStream> = emptySet(),
)
