package io.github.gciatto.ktconstraints.solve.portfolio

import io.github.gciatto.ktconstraints.solve.choco.ChocoBackend
import io.github.gciatto.ktconstraints.solve.cpsat.CpSatBackend
import io.github.gciatto.ktconstraints.solve.minizinc.MiniZincBackend
import io.github.gciatto.ktconstraints.solve.minizinc.MiniZincEngine
import io.github.gciatto.ktconstraints.solve.mip.OrToolsMipBackend
import io.github.gciatto.ktconstraints.solve.spi.SolverBackend
import io.github.gciatto.ktconstraints.solve.spi.SolverRegistry
import io.github.gciatto.ktconstraints.solve.z3.Z3Backend

/** JVM routes. */
actual object DefaultBackends {
    private const val HIGHS_LIMIT_MILLIS = 60_000L

    /** CP-SAT, Choco, Z3, SCIP, HiGHS, and MiniZinc's engines when `MINIZINC_EXECUTABLE` is set. */
    actual suspend fun load(): LoadedBackends {
        val miniZinc = System.getenv("MINIZINC_EXECUTABLE")
        val backends: List<SolverBackend> =
            listOf(CpSatBackend(), ChocoBackend(), Z3Backend(), OrToolsMipBackend.scip(), OrToolsMipBackend.highs(HIGHS_LIMIT_MILLIS)) +
                miniZinc?.let { executable -> MiniZincEngine.entries.map { MiniZincBackend(it, executable) } }.orEmpty()
        return LoadedBackends(SolverRegistry.of(backends))
    }
}
