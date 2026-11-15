package io.github.gciatto.ktconstraints.solve.minizinc

import io.github.gciatto.ktconstraints.solve.spi.SolverBackend

/** Source-compatible Gecode entry point; selection never falls back to another engine. */
class MiniZincGecodeBackend(
    executable: String = "minizinc",
) : SolverBackend by MiniZincBackend(MiniZincEngine.GECODE, executable)
