package io.github.gciatto.ktconstraints.solve.spi

/** Factory of a backend whose runtime starts asynchronously; close the loaded backend to release that runtime. */
interface SolverBackendLoader<out B : SolverBackend> {
    /** Starts the runtime and returns the ready backend, or throws when it cannot start on this platform. */
    suspend fun load(): B
}
