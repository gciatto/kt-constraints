package io.github.gciatto.ktconstraints.interop.examples.howto

import io.kotest.core.spec.style.FunSpec

/** Runs the LP and MILP documentation through the actual Java boundary. */
class LinearProgramsSpec :
    FunSpec({
        test("prove a continuous optimum") { LinearPrograms.linearProgram() }
        test("prove a mixed-integer optimum") { LinearPrograms.mixedIntegerProgram() }
    })
