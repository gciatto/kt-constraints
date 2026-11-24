package io.github.gciatto.ktconstraints.solve.auto

import io.github.gciatto.ktconstraints.solve.choco.ChocoBackend
import io.github.gciatto.ktconstraints.solve.cpsat.CpSatBackend
import io.github.gciatto.ktconstraints.solve.kotlin.KotlinBackend
import io.github.gciatto.ktconstraints.solve.minizinc.MiniZincBackend
import io.github.gciatto.ktconstraints.solve.minizinc.MiniZincEngine
import io.github.gciatto.ktconstraints.solve.mip.OrToolsMipBackend
import io.github.gciatto.ktconstraints.solve.spi.SolverBackend
import io.github.gciatto.ktconstraints.solve.spi.ranking.RankingPolicy
import io.github.gciatto.ktconstraints.solve.z3.Z3Backend

/** JVM routes. */
internal actual object DefaultBackends {
    private const val HIGHS_LIMIT_MILLIS = 60_000L

    /** CP-SAT, Choco, Z3, SCIP, HiGHS, MiniZinc's engines when `MINIZINC_EXECUTABLE` is set, then pure Kotlin. */
    actual suspend fun load(): List<SolverBackend> {
        val miniZinc = System.getenv("MINIZINC_EXECUTABLE")
        val backends: List<SolverBackend> =
            listOf(CpSatBackend(), ChocoBackend(), Z3Backend(), OrToolsMipBackend.scip(), OrToolsMipBackend.highs(HIGHS_LIMIT_MILLIS)) +
                miniZinc?.let { executable -> MiniZincEngine.entries.map { MiniZincBackend(it, executable) } }.orEmpty() +
                KotlinBackend()
        return backends
    }

    /** The default heuristics already prefer the specialised JVM engines; pure Kotlin only answers what they reject. */
    actual fun policy(base: RankingPolicy): RankingPolicy = kotlinAsLastResort(base)
}
