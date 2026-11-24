package io.github.gciatto.ktconstraints.solve.auto

import io.github.gciatto.ktconstraints.solve.spi.registry.SolverRegistry

/** A registry of loaded routes and the runtimes it owns, such as Wasm modules; closing releases them. */
class LoadedBackends(
    /** The loaded routes. */
    val registry: SolverRegistry,
    private val runtimes: List<AutoCloseable> = emptyList(),
) : AutoCloseable {
    override fun close() = runtimes.forEach { it.close() }
}
