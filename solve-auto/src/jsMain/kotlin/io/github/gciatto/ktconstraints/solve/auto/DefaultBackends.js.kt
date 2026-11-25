package io.github.gciatto.ktconstraints.solve.auto

import io.github.gciatto.ktconstraints.solve.highs.HighsJsBackend
import io.github.gciatto.ktconstraints.solve.kotlin.KotlinBackend
import io.github.gciatto.ktconstraints.solve.minizinc.MiniZincJsBackend
import io.github.gciatto.ktconstraints.solve.spi.SolverBackend
import io.github.gciatto.ktconstraints.solve.spi.exception.BackendUnavailableException
import io.github.gciatto.ktconstraints.solve.z3.Z3JsBackend

private val miniZincExecutable: String? =
    js("(typeof process !== 'undefined' && process.env != null) ? (process.env.MINIZINC_EXECUTABLE || null) : null")
        .unsafeCast<String?>()

/** JavaScript routes. */
internal actual object DefaultBackends {
    /**
     * Pure Kotlin, MiniZinc's Node CLI route when `MINIZINC_EXECUTABLE` is set, Z3 Wasm, then HiGHS Wasm. A module
     * that cannot start, such as Z3 in a browser page without cross-origin isolation, leaves the other routes.
     */
    actual suspend fun load(): List<SolverBackend> {
        val z3 =
            try {
                Z3JsBackend.load()
            } catch (_: BackendUnavailableException) {
                null
            }
        val highs =
            try {
                HighsJsBackend.load()
            } catch (_: BackendUnavailableException) {
                null
            }
        val miniZinc = miniZincExecutable?.let { MiniZincJsBackend.loadNode(it) }
        return listOfNotNull(KotlinBackend(), miniZinc, z3, highs)
    }
}
