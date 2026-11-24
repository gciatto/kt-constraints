package io.github.gciatto.ktconstraints.solve.kotlin

import io.github.gciatto.ktconstraints.core.bool.BoolLiteral
import io.github.gciatto.ktconstraints.core.bool.BoolOr
import io.github.gciatto.ktconstraints.core.global.extensional.Element
import io.github.gciatto.ktconstraints.core.integer.IntLiteral
import io.github.gciatto.ktconstraints.core.integer.relation.IntEquality
import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.model.ConstraintScope
import io.github.gciatto.ktconstraints.solve.SolveOptions
import io.github.gciatto.ktconstraints.solve.incremental.CheckStatus
import io.github.gciatto.ktconstraints.solve.result.SolveStatus
import io.github.gciatto.ktconstraints.solve.result.TerminationReason
import io.github.gciatto.ktconstraints.solve.stream.SolutionFound
import io.github.gciatto.ktconstraints.solve.stream.SolveCompleted
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.gciatto.kt.math.BigInteger

class KotlinSessionSafetySpec :
    FunSpec({
        test("an assumed undefined element cannot constrain the next check") {
            with(ConstraintScope.empty()) {
                val x = intVar("x", -1..1)
                val partial = IntEquality(Element(x, listOf(IntLiteral(5))), IntLiteral(5))
                KotlinBackend().create().use { solver ->
                    solver.openSession(build()).use { session ->
                        session.check(listOf(partial)).assignment?.valueOf(x) shouldBe BigInteger.ZERO
                        session.check(listOf(IntEquality(x, IntLiteral(-1)))).status shouldBe CheckStatus.SATISFIABLE
                        session.check(listOf(BoolOr(BoolLiteral(true), partial))).assignment?.valueOf(x) shouldBe BigInteger.ZERO
                    }
                }
            }
        }

        test("a zero-timeout check leaves the session reusable") {
            with(ConstraintScope.empty()) {
                val x = intVar("x", 0..1)
                KotlinBackend().create().use { solver ->
                    solver.openSession(build()).use { session ->
                        session.check(timeoutMillis = 0).terminationReason shouldBe TerminationReason.TIMEOUT
                        session.check(listOf(IntEquality(x, IntLiteral(1)))).assignment?.valueOf(x) shouldBe BigInteger.ONE
                    }
                }
            }
        }

        test("objective element definedness is asserted even without constraints") {
            with(ConstraintScope.empty()) {
                val index = intVar("index", -1..3)
                maximize(Element(index, listOf(IntLiteral(4), IntLiteral(2))))
                KotlinBackend().create().use { solver ->
                    val result = solver.solve(build())
                    result.status shouldBe SolveStatus.OPTIMAL
                    result.objectiveValue shouldBe BigInteger.of(4)
                    result.assignment?.valueOf(index) shouldBe BigInteger.ZERO
                }
            }
        }

        test("an empty projection enumerates one existential witness") {
            val model =
                ConstraintModel.of {
                    intVar("hidden", 0..3)
                    realVar("real")
                    project()
                }
            KotlinBackend().create().use { solver ->
                solver.solutions(model).use { stream ->
                    (stream.next() is SolutionFound) shouldBe true
                    (stream.next() is SolveCompleted) shouldBe true
                    stream.next() shouldBe null
                }
            }
        }

        test("a finite work budget cannot produce infeasibility") {
            val model =
                ConstraintModel.of {
                    intVar("x", 0..10)
                }
            KotlinBackend(KotlinConfiguration(workLimit = 0)).create().use { solver ->
                val result = solver.solve(model)
                result.status shouldBe SolveStatus.UNKNOWN
                result.terminationReason shouldBe TerminationReason.RESOURCE_LIMIT
            }
        }

        test("real witness streams obey their solution limit") {
            val model =
                ConstraintModel.of {
                    realVar("r")
                }
            KotlinBackend().create().use { solver ->
                solver.solutions(model, SolveOptions(solutionLimit = 2)).use { stream ->
                    (stream.next() is SolutionFound) shouldBe true
                    (stream.next() is SolutionFound) shouldBe true
                    val result = (stream.next() as SolveCompleted).result
                    result.status shouldBe SolveStatus.FEASIBLE
                    result.terminationReason shouldBe TerminationReason.RESOURCE_LIMIT
                }
            }
        }
    })
