package io.github.gciatto.ktconstraints.solve.kotlin

import io.github.gciatto.ktconstraints.core.BoolLiteral
import io.github.gciatto.ktconstraints.core.BoolOr
import io.github.gciatto.ktconstraints.core.Element
import io.github.gciatto.ktconstraints.core.IntEquality
import io.github.gciatto.ktconstraints.core.IntLiteral
import io.github.gciatto.ktconstraints.model.ConstraintScope
import io.github.gciatto.ktconstraints.solve.CheckStatus
import io.github.gciatto.ktconstraints.solve.SolutionFound
import io.github.gciatto.ktconstraints.solve.SolveCompleted
import io.github.gciatto.ktconstraints.solve.SolveOptions
import io.github.gciatto.ktconstraints.solve.SolveStatus
import io.github.gciatto.ktconstraints.solve.TerminationReason
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.gciatto.kt.math.BigInteger

class KotlinSessionSafetySpec :
    FunSpec({
        test("an assumed undefined element cannot constrain the next check") {
            val scope = ConstraintScope.empty()
            val x = scope.intVar("x", -1..1)
            val partial = IntEquality(Element(x, listOf(IntLiteral(5))), IntLiteral(5))
            KotlinBackend().create().use { solver ->
                solver.openSession(scope.build()).use { session ->
                    session.check(listOf(partial)).assignment?.valueOf(x) shouldBe BigInteger.ZERO
                    session.check(listOf(IntEquality(x, IntLiteral(-1)))).status shouldBe CheckStatus.SATISFIABLE
                    session.check(listOf(BoolOr(BoolLiteral(true), partial))).assignment?.valueOf(x) shouldBe BigInteger.ZERO
                }
            }
        }

        test("a zero-timeout check leaves the session reusable") {
            val scope = ConstraintScope.empty()
            val x = scope.intVar("x", 0..1)
            KotlinBackend().create().use { solver ->
                solver.openSession(scope.build()).use { session ->
                    session.check(timeoutMillis = 0).terminationReason shouldBe TerminationReason.TIMEOUT
                    session.check(listOf(IntEquality(x, IntLiteral(1)))).assignment?.valueOf(x) shouldBe BigInteger.ONE
                }
            }
        }

        test("objective element definedness is asserted even without constraints") {
            val scope = ConstraintScope.empty()
            val index = scope.intVar("index", -1..3)
            scope.maximize(Element(index, listOf(IntLiteral(4), IntLiteral(2))))
            KotlinBackend().create().use { solver ->
                val result = solver.solve(scope.build())
                result.status shouldBe SolveStatus.OPTIMAL
                result.objectiveValue shouldBe BigInteger.of(4)
                result.assignment?.valueOf(index) shouldBe BigInteger.ZERO
            }
        }

        test("an empty projection enumerates one existential witness") {
            val scope = ConstraintScope.empty()
            scope.intVar("hidden", 0..3)
            scope.realVar("real")
            scope.project()
            KotlinBackend().create().use { solver ->
                solver.solutions(scope.build()).use { stream ->
                    (stream.next() is SolutionFound) shouldBe true
                    (stream.next() is SolveCompleted) shouldBe true
                    stream.next() shouldBe null
                }
            }
        }

        test("a finite work budget cannot produce infeasibility") {
            val scope = ConstraintScope.empty()
            scope.intVar("x", 0..10)
            KotlinBackend(KotlinConfiguration(workLimit = 0)).create().use { solver ->
                val result = solver.solve(scope.build())
                result.status shouldBe SolveStatus.UNKNOWN
                result.terminationReason shouldBe TerminationReason.RESOURCE_LIMIT
            }
        }

        test("real witness streams obey their solution limit") {
            val scope = ConstraintScope.empty()
            scope.realVar("r")
            KotlinBackend().create().use { solver ->
                solver.solutions(scope.build(), SolveOptions(solutionLimit = 2)).use { stream ->
                    (stream.next() is SolutionFound) shouldBe true
                    (stream.next() is SolutionFound) shouldBe true
                    val result = (stream.next() as SolveCompleted).result
                    result.status shouldBe SolveStatus.FEASIBLE
                    result.terminationReason shouldBe TerminationReason.RESOURCE_LIMIT
                }
            }
        }
    })
