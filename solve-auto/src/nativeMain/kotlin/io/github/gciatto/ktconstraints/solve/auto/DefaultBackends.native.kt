package io.github.gciatto.ktconstraints.solve.auto

import io.github.gciatto.ktconstraints.solve.spi.registry.SolverRegistry
import io.github.gciatto.ktconstraints.solve.z3.Z3NativeBackend

/** Native routes. */
actual object DefaultBackends {
    /** Z3 through its C API, loaded at run time from `KT_CONSTRAINTS_Z3_LIBRARY` or the loader path. */
    actual suspend fun load(): LoadedBackends = LoadedBackends(SolverRegistry.of(Z3NativeBackend()))
}
