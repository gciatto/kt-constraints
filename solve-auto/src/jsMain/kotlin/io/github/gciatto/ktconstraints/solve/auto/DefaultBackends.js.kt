package io.github.gciatto.ktconstraints.solve.auto

import io.github.gciatto.ktconstraints.solve.minizinc.MiniZincJsBackend
import io.github.gciatto.ktconstraints.solve.spi.SolverBackend
import io.github.gciatto.ktconstraints.solve.z3.Z3JsBackend

private val miniZincExecutable: String? =
    js("(typeof process !== 'undefined' && process.env != null) ? (process.env.MINIZINC_EXECUTABLE || null) : null")
        .unsafeCast<String?>()

/** JavaScript routes. */
internal actual object DefaultBackends {
    /** Z3 Wasm, plus MiniZinc's Node CLI route when `MINIZINC_EXECUTABLE` is set. */
    actual suspend fun load(): List<SolverBackend> {
        val z3 = Z3JsBackend.load()
        val miniZinc = miniZincExecutable?.let { MiniZincJsBackend.loadNode(it) }
        val loaded = listOfNotNull(z3, miniZinc)
        return loaded
    }
}
