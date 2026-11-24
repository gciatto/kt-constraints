package io.github.gciatto.ktconstraints.solve.auto

import io.github.gciatto.ktconstraints.solve.spi.SolverBackend
import io.github.gciatto.ktconstraints.solve.z3.Z3NativeBackend

/** Native routes. */
internal actual object DefaultBackends {
    /** Z3 through its C API, loaded at run time from `KT_CONSTRAINTS_Z3_LIBRARY` or the loader path. */
    actual suspend fun load(): List<SolverBackend> = listOf(Z3NativeBackend())
}
