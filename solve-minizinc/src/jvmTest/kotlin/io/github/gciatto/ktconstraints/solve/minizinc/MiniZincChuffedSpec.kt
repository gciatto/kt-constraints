package io.github.gciatto.ktconstraints.solve.minizinc

import io.github.gciatto.ktconstraints.test.solve.solverContract
import io.kotest.core.spec.style.FunSpec

/** Real chuffed conformance, enabled explicitly by the runtime test configuration. */
class MiniZincChuffedSpec : FunSpec({
    val executable = System.getenv("MINIZINC_EXECUTABLE")
    val engines = System.getenv("MINIZINC_ENGINES")?.split(',').orEmpty()
    if (executable == null || "chuffed" !in engines) {
        test("requires-explicitly-requested-chuffed-runtime").config(enabled = false) {}
    } else {
        include(solverContract { MiniZincBackend(MiniZincEngine.CHUFFED, executable).create() })
    }
})
