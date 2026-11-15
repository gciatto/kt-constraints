package io.github.gciatto.ktconstraints.solve.minizinc

import io.github.gciatto.ktconstraints.test.solve.solverContract
import io.kotest.core.spec.style.FunSpec

/** Real cp-sat conformance, enabled explicitly by the runtime test configuration. */
class MiniZincCpSatSpec : FunSpec({
    val executable = System.getenv("MINIZINC_EXECUTABLE")
    val engines = System.getenv("MINIZINC_ENGINES")?.split(',').orEmpty()
    if (executable == null || "cp-sat" !in engines) {
        test("requires-explicitly-requested-cp-sat-runtime").config(enabled = false) {}
    } else {
        include(solverContract { MiniZincBackend(MiniZincEngine.CP_SAT, executable).create() })
    }
})
