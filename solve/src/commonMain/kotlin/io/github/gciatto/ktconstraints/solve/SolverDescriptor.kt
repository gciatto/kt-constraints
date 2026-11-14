package io.github.gciatto.ktconstraints.solve

/** Identifies an engine and its route (direct API versus MiniZinc compilation). */
data class SolverDescriptor(
    val engine: String,
    val route: String,
    val capabilities: Set<SolverCapability>,
)
