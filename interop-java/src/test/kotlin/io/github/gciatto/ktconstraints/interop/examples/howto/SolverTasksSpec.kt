package io.github.gciatto.ktconstraints.interop.examples.howto

import io.kotest.core.spec.style.FunSpec

/** Runs the how-to examples compiled by javac. */
class SolverTasksSpec :
    FunSpec({
        test("optimize asynchronously") { SolverTasks.optimize() }
        test("load the automatic backend") { SolverTasks.auto() }
        test("enumerate") { SolverTasks.enumerate() }
        test("restore sessions") { SolverTasks.session() }
        test("restore stores") { SolverTasks.store() }
        test("cancel asynchronous search") { SolverTasks.cancel() }
        test("preserve decimal input") { SolverTasks.real() }
    })
