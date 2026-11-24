package io.github.gciatto.ktconstraints.solve.z3

import io.github.gciatto.ktconstraints.solve.spi.SolverBackend

/** Platform-independent entry point to the direct Z3 route of the running platform. */
object Z3 {
    /**
     * Loads [Z3Backend] on the JVM, [Z3NativeBackend] on Kotlin/Native and [Z3JsBackend] on JavaScript.
     * Close the returned backend to release its runtime.
     */
    suspend fun loadBackend(): SolverBackend = loadPlatformBackend()
}

internal expect suspend fun loadPlatformBackend(): SolverBackend
