package io.github.gciatto.ktconstraints.solve.auto

import io.github.gciatto.ktconstraints.solve.spi.SolverBackend

/**
 * Routes bundled for the current platform, in tie-breaking order:
 * - JVM: CP-SAT, Choco, Z3, SCIP, HiGHS (60 s per-solve limit, since OR-Tools cannot interrupt it), and the
 *   MiniZinc engines when `MINIZINC_EXECUTABLE` is set;
 * - Node: Z3 Wasm, and MiniZinc's CLI route when `MINIZINC_EXECUTABLE` is set;
 * - browsers: Z3 Wasm, which needs cross-origin isolation;
 * - Native: Z3 through its C API, loaded at run time.
 * Routes whose runtime is missing stay registered and are rejected, with reasons, during ranking.
 */
internal expect object DefaultBackends {
    /** Loads the bundled routes; closeable ones own asynchronously loaded runtimes. */
    suspend fun load(): List<SolverBackend>
}
