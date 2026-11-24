package io.github.gciatto.ktconstraints.solve.z3

import io.github.gciatto.ktconstraints.solve.spi.SolverBackend

internal actual suspend fun loadPlatformBackend(): SolverBackend = Z3JsBackend.load()
