package io.github.gciatto.ktconstraints.solve.auto

import io.github.gciatto.ktconstraints.solve.kotlin.KotlinBackend
import io.github.gciatto.ktconstraints.solve.spi.SolverBackend
import io.github.gciatto.ktconstraints.solve.spi.ranking.RankingPolicy
import io.github.gciatto.ktconstraints.solve.z3.Z3NativeBackend

/** Native routes. */
internal actual object DefaultBackends {
    /** Z3 through its C API, loaded at run time from `KT_CONSTRAINTS_Z3_LIBRARY` or the loader path, then pure Kotlin. */
    actual suspend fun load(): List<SolverBackend> = listOf(Z3NativeBackend(), KotlinBackend())

    /** Z3 first when its library loads; pure Kotlin answers what it rejects, or everything when it is missing. */
    actual fun policy(base: RankingPolicy): RankingPolicy = kotlinAsLastResort(base)
}
