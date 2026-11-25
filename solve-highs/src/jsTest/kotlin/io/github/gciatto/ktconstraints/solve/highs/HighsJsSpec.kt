package io.github.gciatto.ktconstraints.solve.highs

import io.github.gciatto.ktconstraints.solve.NumericTolerance
import io.github.gciatto.ktconstraints.test.solve.linearProgrammingContract
import io.github.gciatto.ktconstraints.test.solve.solverContract
import io.kotest.core.spec.style.FunSpec

/** The highs Wasm route passes the shared contracts in Node and browsers. */
class HighsJsSpec :
    FunSpec({
        var backend: HighsJsBackend? = null
        var loadFailure: Throwable? = null
        // Kotest JS drops a spec whose beforeSpec throws, so report load failures from every test instead.
        beforeSpec { backend = runCatching { HighsJsBackend.load() }.onFailure { loadFailure = it }.getOrNull() }
        val loaded = { backend ?: throw IllegalStateException("highs did not load: $loadFailure", loadFailure) }
        include(solverContract(enumeration = false, globals = false) { loaded().createSolver() })
        include(linearProgrammingContract(NumericTolerance()) { loaded().createSolver() })
    })
