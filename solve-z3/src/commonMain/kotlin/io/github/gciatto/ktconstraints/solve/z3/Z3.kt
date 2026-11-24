package io.github.gciatto.ktconstraints.solve.z3

import io.github.gciatto.ktconstraints.solve.spi.SolverBackend
import io.github.gciatto.ktconstraints.solve.spi.SolverBackendLoader

/** Platform-independent loader of the direct Z3 route of the running platform. */
object Z3 : SolverBackendLoader<SolverBackend> {
    /**
     * Loads [Z3Backend] on the JVM, [Z3NativeBackend] on Kotlin/Native and [Z3JsBackend] on JavaScript.
     * Close the returned backend to release its runtime.
     */
    override suspend fun load(): SolverBackend = loadPlatformBackend()
}

internal expect suspend fun loadPlatformBackend(): SolverBackend
