package io.github.gciatto.ktconstraints.solve.kotlin.sat

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlin.coroutines.cancellation.CancellationException

/** Interruptions leave a reusable formula and never become infeasibility evidence. */
class SatInterruptionSpec :
    FunSpec({
        test("sat/interrupts-while-scanning-a-long-clause-and-can-resume") {
            val solver = SatSolver()
            val variables = IntArray(4096) { solver.newVariable() }
            solver.addClause(variables)
            for (variable in variables.dropLast(1)) solver.addClause(intArrayOf(-variable))
            var polls = 0
            shouldThrow<CancellationException> {
                solver.solve {
                    if (++polls == 24) throw CancellationException("stop")
                }
            }
            (polls >= 24) shouldBe true
            requireNotNull(solver.solve().values)[variables.last()] shouldBe true
        }

        test("sat/invalid-assumptions-preserve-the-next-check") {
            val solver = SatSolver()
            val a = solver.newVariable()
            for (literal in listOf(0, Int.MIN_VALUE, a + 1)) {
                shouldThrow<IllegalArgumentException> { solver.solve(intArrayOf(literal)) }
                requireNotNull(solver.solve(intArrayOf(a)).values)[a] shouldBe true
            }
        }

        test("sat/rejects-mutation-and-reentrant-solving-during-a-check") {
            val solver = SatSolver()
            val a = solver.newVariable()
            val result =
                solver.solve {
                    shouldThrow<IllegalStateException> { solver.newVariable() }
                    shouldThrow<IllegalStateException> { solver.addClause(intArrayOf(-a)) }
                    shouldThrow<IllegalStateException> { solver.solve() }
                }
            result.status shouldBe SatStatus.SATISFIABLE
            solver.addClause(intArrayOf(a))
            requireNotNull(solver.solve().values)[a] shouldBe true
        }

        test("sat/copies-assumptions-before-the-first-suspension") {
            val solver = SatSolver()
            val a = solver.newVariable()
            val assumptions = intArrayOf(a)
            val result = solver.solve(assumptions) { assumptions[0] = -a }
            requireNotNull(result.values)[a] shouldBe true
        }
    })
