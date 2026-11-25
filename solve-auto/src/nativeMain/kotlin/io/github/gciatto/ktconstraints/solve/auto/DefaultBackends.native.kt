package io.github.gciatto.ktconstraints.solve.auto

import io.github.gciatto.ktconstraints.solve.highs.HighsNativeBackend
import io.github.gciatto.ktconstraints.solve.kotlin.KotlinBackend
import io.github.gciatto.ktconstraints.solve.minizinc.MiniZincBackend
import io.github.gciatto.ktconstraints.solve.minizinc.MiniZincEngine
import io.github.gciatto.ktconstraints.solve.spi.SolverBackend
import io.github.gciatto.ktconstraints.solve.z3.Z3NativeBackend
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import platform.posix.getenv

/** Native routes. */
internal actual object DefaultBackends {
    /**
     * Pure Kotlin, HiGHS through its C API (loaded at run time from `KT_CONSTRAINTS_HIGHS_LIBRARY` or the loader path), Z3
     * through its C API (likewise, from `KT_CONSTRAINTS_Z3_LIBRARY`), then MiniZinc's engines when
     * `MINIZINC_EXECUTABLE` is set. MiniZinc runs on Linux and macOS only.
     */
    @OptIn(ExperimentalForeignApi::class)
    actual suspend fun load(): List<SolverBackend> {
        val miniZinc = getenv("MINIZINC_EXECUTABLE")?.toKString()
        return listOf(KotlinBackend(), HighsNativeBackend(), Z3NativeBackend()) +
            miniZinc?.let { executable -> MiniZincEngine.entries.map { MiniZincBackend(it, executable) } }.orEmpty()
    }
}
