package io.github.gciatto.ktconstraints.solve.minizinc

import io.github.gciatto.ktconstraints.model.ConstraintScope
import io.github.gciatto.ktconstraints.solve.SolutionFound
import io.github.gciatto.ktconstraints.solve.SolveOptions
import io.github.gciatto.ktconstraints.solve.TerminationReason
import io.github.gciatto.ktconstraints.test.solve.solverContract
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/** Same real contracts for Node CLI and browser Wasm; route identity records which actually ran. */
class MiniZincJsSpec :
    FunSpec({
        val browser = js("typeof window !== 'undefined'") as Boolean
        val executable =
            if (browser) {
                null
            } else {
                js(
                    "typeof process !== 'undefined' ? process.env.MINIZINC_EXECUTABLE || null : null",
                ) as String?
            }
        if (!browser && executable == null) {
            test("requires-requested-node-cli-or-browser-wasm").config(enabled = false) {}
        } else {
            var backend: MiniZincJsBackend? = null
            val load: suspend () -> MiniZincJsBackend = {
                if (browser) {
                    val origin = js("globalThis.location.origin") as String
                    MiniZincJsBackend.loadBrowser(
                        "$origin/minizinc/minizinc-worker.js",
                        "$origin/minizinc/minizinc.wasm",
                        "$origin/minizinc/minizinc.data",
                    )
                } else {
                    MiniZincJsBackend.loadNode(requireNotNull(executable))
                }
            }
            beforeSpec { backend = runCatching { load() }.onFailure { console.error(it.stackTraceToString()) }.getOrThrow() }
            afterSpec { backend?.close() }
            include(solverContract { requireNotNull(backend).create() })
            test("js/route-identifies-the-actual-transport") {
                val selected = requireNotNull(backend)
                selected.descriptor.id shouldBe if (browser) "chuffed/minizinc-wasm" else "gecode/minizinc-js-cli"
                selected.create().use { solver ->
                    solver.solve(ConstraintScope.empty().build()).descriptor shouldBe selected.descriptor
                }
            }
            test("js/early-close-cancels-a-real-enumeration") {
                val scope = ConstraintScope.empty()
                scope.intVar("x", 0..1_000_000)
                requireNotNull(backend).create().use { solver ->
                    val stream = solver.solutions(scope.build())
                    (stream.next() is SolutionFound) shouldBe true
                    stream.close()
                    stream.terminalResult?.terminationReason shouldBe TerminationReason.CANCELLED
                }
            }
            test("js/rejects-a-transport-for-the-wrong-environment") {
                val failure =
                    shouldThrow<IllegalStateException> {
                        if (browser) MiniZincJsBackend.loadNode() else MiniZincJsBackend.loadBrowser("worker", "wasm", "data")
                    }
                failure.message shouldBe if (browser) "Node CLI loading requires Node.js" else "Wasm loading requires a browser"
            }
            test("js/backend-close-cancels-streams-and-allows-a-new-runtime-owner") {
                val selected = requireNotNull(backend)
                val solver = selected.create()
                val scope = ConstraintScope.empty()
                scope.intVar("x", 0..1_000_000)
                val stream = solver.solutions(scope.build())
                (stream.next() is SolutionFound) shouldBe true
                selected.close()
                selected.close()
                selected.availability().available shouldBe false
                stream.terminalResult?.terminationReason shouldBe TerminationReason.CANCELLED
                shouldThrow<IllegalStateException> { solver.solutions(ConstraintScope.empty().build()) }
                solver.close()
                backend = load()
                requireNotNull(backend).availability().available shouldBe true
            }
            test("js/node-backend-close-lets-cancelled-cli-runs-remove-temporary-files").config(enabled = !browser) {
                val fs = js("require('node:fs')")
                val previous = js("process.env.TMPDIR")
                val isolated = fs.mkdtempSync(js("require('node:os').tmpdir()") + "/kt-constraints-") as String
                js("process.env").TMPDIR = isolated
                try {
                    val selected = requireNotNull(backend)
                    val scope = ConstraintScope.empty()
                    scope.intVar("x", 0..1_000_000)
                    val stream = selected.create().solutions(scope.build())
                    (stream.next() is SolutionFound) shouldBe true
                    selected.close()
                    backend = load()
                    (fs.readdirSync(isolated) as Array<String>).toList() shouldBe emptyList()
                } finally {
                    if (previous == null) js("delete process.env.TMPDIR") else js("process.env").TMPDIR = previous
                    fs.rmSync(isolated, js("({recursive: true, force: true})"))
                }
            }
            test("js/owned-deadline-stops-real-execution") {
                val scope = ConstraintScope.empty()
                scope.intVar("x", 0..1_000_000)
                requireNotNull(backend).create().use { solver ->
                    solver.solve(scope.build(), SolveOptions(timeoutMillis = 10)).terminationReason shouldBe TerminationReason.TIMEOUT
                }
            }
        }
    })
