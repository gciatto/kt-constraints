package io.github.gciatto.ktconstraints.solve.minizinc

import io.github.gciatto.ktconstraints.test.solve.solverContract
import io.kotest.core.spec.style.FunSpec

/** Real cbc contracts; complete enumeration is explicitly unsupported. */
class MiniZincCbcSpec : FunSpec({
    val executable = System.getenv("MINIZINC_EXECUTABLE")
    val engines = System.getenv("MINIZINC_ENGINES")?.split(',').orEmpty()
    if (executable == null || "cbc" !in engines) {
        test("requires-explicitly-requested-cbc-runtime").config(enabled = false) {}
    } else {
        include(solverContract(enumeration = false) { MiniZincBackend(MiniZincEngine.CBC, executable).create() })
    }
})
