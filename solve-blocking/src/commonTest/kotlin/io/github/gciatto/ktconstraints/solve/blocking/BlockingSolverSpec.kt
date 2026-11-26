package io.github.gciatto.ktconstraints.solve.blocking

import io.github.gciatto.ktconstraints.core.Rational
import io.github.gciatto.ktconstraints.core.integer.IntToReal
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

        test("solve returns exact optima of mixed-integer programs") {
            val scope = ConstraintScope.empty()
            val x = scope.realVar("x")
            val n = scope.intVar("n", 0..5)
            scope.add(x le IntToReal(n) * Rational.of(1, 3))
            scope.add(IntToReal(n) + x le Rational.of(7, 2))
            scope.maximize(x)
            val result = KotlinBackend().withBlockingSolver { it.solve(scope.buildModel()) }
            result.status shouldBe SolveStatus.OPTIMAL
            // n = 2 gives x = 2/3; n = 3 only x = 1/2.
            result.realObjectiveValue shouldBe Rational.of(2, 3)
            result.metadata.statistics["technique"] shouldBe "branch-and-bound"
        }

        test("sessions decide rational linear feasibility across checkpoints") {
            val scope = ConstraintScope.empty()
            val x = scope.realVar("x")
            val y = scope.realVar("y")
            scope.add(x + y le 1)
            KotlinBackend().withBlockingSolver { solver ->
                solver.openSession(scope.buildModel()).use { session ->
                    val before = session.push()
                    session.add(x gt Rational.of(1, 2))
                    session.add(y gt Rational.of(1, 2))
                    session.check().status shouldBe CheckStatus.UNSATISFIABLE
                    session.popTo(before)
                    val result = session.check(listOf(x gt Rational.of(1, 2)))
                    result.status shouldBe CheckStatus.SATISFIABLE
                    (requireNotNull(requireNotNull(result.assignment).valueOf(x)) > Rational.of(1, 2)) shouldBe true
                }
            }
        }
    })
