package io.github.gciatto.ktconstraints.solve.blocking

import io.github.gciatto.ktconstraints.model.ConstraintScope
import io.github.gciatto.ktconstraints.solve.incremental.CheckStatus
import io.github.gciatto.ktconstraints.solve.kotlin.KotlinBackend
import io.github.gciatto.ktconstraints.solve.result.SolveStatus
import io.github.gciatto.ktconstraints.solve.result.TerminationReason
import io.github.gciatto.ktconstraints.solve.stream.SolutionFound
import io.github.gciatto.ktconstraints.solve.stream.SolveCompleted
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/** Blocking calls over the portable backend, on every platform with blocking threads. */
class BlockingSolverSpec :
    FunSpec({
        test("solve blocks until the optimum") {
            val scope = ConstraintScope.empty()
            val x = scope.intVar("x", 0..3)
            val y = scope.intVar("y", 0..3)
            scope.add(x + y eq 3)
            scope.maximize(x)
            val result = KotlinBackend().withBlockingSolver { it.solve(scope.buildModel()) }
            result.status shouldBe SolveStatus.OPTIMAL
            result.assignment?.intValueOf(x) shouldBe 3
        }

        test("solutions iterate with for, then complete once") {
            val scope = ConstraintScope.empty()
            val x = scope.intVar("x", 0..2)
            val found = mutableListOf<Int?>()
            val completions = mutableListOf<SolveCompleted>()
            KotlinBackend().createSolver().blocking().use { solver ->
                solver.solutions(scope.buildModel()).use { stream ->
                    for (event in stream) {
                        when (event) {
                            is SolutionFound -> found += event.assignment.intValueOf(x)
                            else -> completions += event.shouldBeInstanceOf<SolveCompleted>()
                        }
                    }
                    stream.hasNext() shouldBe false
                    shouldThrow<NoSuchElementException> { stream.next() }
                }
            }
            found shouldContainExactlyInAnyOrder listOf(0, 1, 2)
            completions.single().result.terminationReason shouldBe TerminationReason.COMPLETED
        }

        test("sessions restore checkpoints") {
            val scope = ConstraintScope.empty()
            val x = scope.intVar("x", 0..3)
            scope.add(x ge 1)
            KotlinBackend().withBlockingSolver { solver ->
                solver.openSession(scope.buildModel()).use { session ->
                    val before = session.push()
                    session.add(x eq 0)
                    session.check().status shouldBe CheckStatus.UNSATISFIABLE
                    session.popTo(before)
                    session.check(listOf(x eq 2)).assignment?.intValueOf(x) shouldBe 2
                }
            }
        }
    })
