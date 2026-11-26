package io.github.gciatto.ktconstraints.interop.examples.howto

import io.kotest.core.spec.style.FunSpec

/** Runs the how-to examples compiled by javac. */
class SolverTasksSpec :
    FunSpec({
        test("optimize asynchronously") { SolverTasks.optimize() }
        test("load the automatic backend") { SolverTasks.auto() }
        test("prove a mixed-integer optimum") { SolverTasks.mixedIntegerProgram() }
        test("solve a program in floating point") { SolverTasks.floatingPointProgram() }
        test("enumerate") { SolverTasks.enumerate() }
        test("restore sessions") { SolverTasks.session() }
        test("re-optimize sessions") { SolverTasks.sessionOptimization() }
        test("restore stores") { SolverTasks.store() }
        test("cancel asynchronous search") { SolverTasks.cancel() }
        test("preserve decimal input") { SolverTasks.real() }
    })
