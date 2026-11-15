package io.github.gciatto.ktconstraints.solve.z3

import io.github.gciatto.ktconstraints.core.AllDifferent
import io.github.gciatto.ktconstraints.core.BoolLiteral
import io.github.gciatto.ktconstraints.core.Element
import io.github.gciatto.ktconstraints.core.IntDomain
import io.github.gciatto.ktconstraints.core.IntEquality
import io.github.gciatto.ktconstraints.core.IntLiteral
import io.github.gciatto.ktconstraints.core.IntVar
import io.github.gciatto.ktconstraints.core.LinearSum
import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.model.ConstraintScope
import io.github.gciatto.ktconstraints.solve.CheckStatus
import io.github.gciatto.ktconstraints.solve.ConstraintSolver
import io.github.gciatto.ktconstraints.solve.SolutionFound
import io.github.gciatto.ktconstraints.solve.SolveCompleted
import io.github.gciatto.ktconstraints.solve.SolveOptions
import io.github.gciatto.ktconstraints.solve.SolveStatus
import io.github.gciatto.ktconstraints.solve.TerminationReason
import io.github.gciatto.ktconstraints.test.solve.FiniteOracle
import io.github.gciatto.ktconstraints.test.solve.incrementalContract
import io.github.gciatto.ktconstraints.test.solve.solverContract
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import org.gciatto.kt.math.BigInteger

/** Official z3-solver Wasm on Node: shared contracts, exact integers, oracle differential and interruption. */
class Z3JsSpec :
    FunSpec({
        val node = js("typeof window === 'undefined'") as Boolean
        if (!node) {
            test("z3-js/requires-node-or-a-cross-origin-isolated-browser").config(enabled = false) {}
        } else {
            var backend: Z3JsBackend? = null
            var loadFailure: Throwable? = null
            // Kotest JS drops a spec whose beforeSpec throws, so report load failures from every test instead.
            beforeSpec { backend = runCatching { Z3JsBackend.load() }.onFailure { loadFailure = it }.getOrNull() }
            val loaded = { backend ?: throw IllegalStateException("z3-solver did not load: $loadFailure", loadFailure) }
            afterSpec { backend?.close() }

            include(solverContract { loaded().create() })
            include(incrementalContract { loaded().create() })

            test("z3-js/route-identifies-the-wasm-binding") {
                val selected = loaded()
                selected.descriptor.id shouldBe "z3/wasm"
                selected.availability().available shouldBe true
                selected.create().use { it.solve(ConstraintScope.empty().build()).descriptor shouldBe selected.descriptor }
            }

            test("z3-js/integers-beyond-64-bits-are-exact") {
                val huge = BigInteger.of("1000000000000000000000000000001")
                val scope = ConstraintScope.empty()
                val x = scope.intVar("x", IntDomain.of(-huge, huge))
                scope.add(
                    IntEquality(LinearSum(listOf(x), listOf(BigInteger.TWO), BigInteger.ONE), IntLiteral(huge + huge + BigInteger.ONE)),
                )
                loaded().create().use { solver ->
                    val result = solver.solve(scope.build())
                    result.status shouldBe SolveStatus.FEASIBLE
                    result.assignment?.valueOf(x) shouldBe huge
                }
            }

            test("z3-js/element-definedness-matches-the-oracle") {
                val scope = ConstraintScope.empty()
                val index = scope.intVar("i", -1..4)
                val value = scope.intVar("v", -2..3)
                scope.add(IntEquality(Element(index, listOf(IntLiteral(-2), value, IntLiteral(3))), value))
                val model = scope.build()
                answers(loaded().create(), model, index, value) shouldBe answers(FiniteOracle(), model, index, value)
            }

            test("z3-js/owned-deadline-stops-a-hard-check") {
                loaded().create().use { solver ->
                    val result = solver.solve(pigeonhole(), SolveOptions(timeoutMillis = 200))
                    result.status shouldBe SolveStatus.UNKNOWN
                    result.terminationReason shouldBe TerminationReason.TIMEOUT
                }
            }

            test("z3-js/session-stays-usable-after-a-cancelled-check") {
                loaded().create().use { solver ->
                    solver.openSession(pigeonhole()).use { session ->
                        withTimeoutOrNull(300) { session.check() } shouldBe null
                        session.check(listOf(BoolLiteral(false))).status shouldBe CheckStatus.UNSATISFIABLE
                    }
                }
            }

            test("z3-js/session-close-interrupts-an-active-check") {
                val solver = loaded().create()
                val session = solver.openSession(pigeonhole())
                coroutineScope {
                    val check = async { session.check() }
                    delay(300)
                    solver.close()
                    val result = check.await()
                    result.status shouldBe CheckStatus.UNKNOWN
                    result.terminationReason shouldBe TerminationReason.CANCELLED
                }
            }
        }
    })

private fun pigeonhole(holes: Int = 12): ConstraintModel {
    val scope = ConstraintScope.empty()
    scope.add(AllDifferent((0..holes).map { scope.intVar("p$it", 0 until holes) }))
    return scope.build()
}

private suspend fun answers(
    solver: ConstraintSolver,
    model: ConstraintModel,
    first: IntVar,
    second: IntVar,
): Set<List<BigInteger?>> =
    solver.use {
        val values = mutableSetOf<List<BigInteger?>>()
        solver.solutions(model).use { stream ->
            var event = stream.next()
            while (event is SolutionFound) {
                values += listOf(event.assignment.valueOf(first), event.assignment.valueOf(second))
                event = stream.next()
            }
            check(event is SolveCompleted && event.result.terminationReason == TerminationReason.COMPLETED) { "Missing completion" }
        }
        values
    }
