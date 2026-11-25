package io.github.gciatto.ktconstraints.solve.auto

import io.github.gciatto.ktconstraints.solve.spi.SolverBackend

/**
 * Routes bundled for the current platform, fastest first according to the recorded route benchmarks:
 * - JVM: CP-SAT, Choco, pure Kotlin, Z3, SCIP, HiGHS (60 s per-solve limit, since OR-Tools cannot interrupt it), then
 *   the MiniZinc engines when `MINIZINC_EXECUTABLE` is set;
 * - JS: pure Kotlin, MiniZinc's Node CLI route when `MINIZINC_EXECUTABLE` is set, Z3 Wasm (which needs
 *   cross-origin isolation in browsers), then HiGHS Wasm;
 * - Native: pure Kotlin, Z3 and HiGHS through their C APIs, loaded at run time, then the MiniZinc engines when
 *   `MINIZINC_EXECUTABLE` is set (Linux and macOS only).
 * Routes whose runtime is missing stay registered and are rejected, with reasons, during ranking.
 */
internal expect object DefaultBackends {
    /** Loads the bundled routes in decreasing performance order; closeable ones own asynchronously loaded runtimes. */
    suspend fun load(): List<SolverBackend>
}
