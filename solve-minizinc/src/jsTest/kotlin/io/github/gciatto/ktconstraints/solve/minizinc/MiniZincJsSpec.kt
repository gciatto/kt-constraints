package io.github.gciatto.ktconstraints.solve.minizinc

import io.github.gciatto.ktconstraints.model.ConstraintScope
import io.github.gciatto.ktconstraints.solve.SolutionFound
import io.github.gciatto.ktconstraints.solve.SolveOptions
import io.github.gciatto.ktconstraints.solve.TerminationReason
import io.github.gciatto.ktconstraints.test.solve.solverContract
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.seconds

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
            var loadFailure: Throwable? = null
            // Kotest JS drops a spec whose beforeSpec throws, so report load failures from every test instead.
            beforeSpec { backend = runCatching { load() }.onFailure { loadFailure = it }.getOrNull() }
            val loaded = { backend ?: throw IllegalStateException("MiniZinc JS runtime did not load: $loadFailure", loadFailure) }
            afterSpec { backend?.close() }
            include(solverContract { loaded().create() })
            test("js/route-identifies-the-actual-transport") {
                val selected = loaded()
                selected.descriptor.id shouldBe if (browser) "chuffed/minizinc-wasm" else "gecode/minizinc-js-cli"
                selected.create().use { solver ->
                    solver.solve(ConstraintScope.empty().build()).descriptor shouldBe selected.descriptor
                }
            }
            test("js/early-close-cancels-a-real-enumeration") {
                val scope = ConstraintScope.empty()
                scope.intVar("x", 0..1_000_000)
                loaded().create().use { solver ->
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
                val selected = loaded()
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
                loaded().availability().available shouldBe true
            }
            test("js/node-backend-close-lets-cancelled-cli-runs-remove-temporary-files").config(enabled = !browser) {
                val fs = js("process.getBuiltinModule('node:fs')")
                val previous = js("process.env.TMPDIR")
                val isolated = fs.mkdtempSync(js("process.getBuiltinModule('node:os').tmpdir()") + "/kt-constraints-") as String
                val restore = { if (previous == null) js("delete process.env.TMPDIR") else js("process.env").TMPDIR = previous }
                js("process.env").TMPDIR = isolated
                try {
                    val selected = loaded()
                    val scope = ConstraintScope.empty()
                    scope.intVar("x", 0..1_000_000)
                    val stream = selected.create().solutions(scope.build())
                    (stream.next() is SolutionFound) shouldBe true
                    selected.close()
                    restore()
                    backend = load()
                    // The SDK removes its own run directory asynchronously after emitting exit.
                    eventually(2.seconds) { (fs.readdirSync(isolated) as Array<String>).toList() shouldBe emptyList() }
                } finally {
                    restore()
                    fs.rmSync(isolated, js("({recursive: true, force: true})"))
                }
            }
            test("js/owned-deadline-stops-real-execution") {
                val scope = ConstraintScope.empty()
                scope.intVar("x", 0..1_000_000)
                loaded().create().use { solver ->
                    solver.solve(scope.build(), SolveOptions(timeoutMillis = 10)).terminationReason shouldBe TerminationReason.TIMEOUT
                }
            }
        }
    })
