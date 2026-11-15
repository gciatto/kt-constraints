package io.github.gciatto.ktconstraints.test.solve

import io.github.gciatto.ktconstraints.core.BoolAnd
import io.github.gciatto.ktconstraints.core.BoolExpr
import io.github.gciatto.ktconstraints.core.BoolLiteral
import io.github.gciatto.ktconstraints.core.BoolNot
import io.github.gciatto.ktconstraints.core.Element
import io.github.gciatto.ktconstraints.core.IntDomain
import io.github.gciatto.ktconstraints.core.IntEquality
import io.github.gciatto.ktconstraints.core.IntLiteral
import io.github.gciatto.ktconstraints.core.IntProduct
import io.github.gciatto.ktconstraints.model.ConstraintScope
import io.github.gciatto.ktconstraints.solve.SolutionFound
import io.github.gciatto.ktconstraints.solve.SolveCompleted
import io.github.gciatto.ktconstraints.solve.SolveOptions
import io.github.gciatto.ktconstraints.solve.SolveStatus
import io.github.gciatto.ktconstraints.solve.TerminationReason
import io.github.gciatto.ktconstraints.solve.spi.UnsupportedModelException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.gciatto.kt.math.BigInteger

/** The test-only finite oracle is exact, bounded and honest about incomplete search. */
class FiniteOracleSpec :
    FunSpec({
        include(solverContract { FiniteOracle() })

        test("oracle/variable-cap-also-bounds-fixed-domain-models") {
            val scope = ConstraintScope.empty()
            repeat(FiniteOracle.MAX_VARIABLES + 1) { scope.intVar("x$it", 0..0) }
            FiniteOracle().use { solver ->
                shouldThrow<UnsupportedModelException> { solver.solve(scope.build()) }
            }
        }
        test("oracle/sat-and-unsat-with-boolean-variables") {
            val scope = ConstraintScope.empty()
            val flag = scope.boolVar("flag")
            scope.add(flag)
            FiniteOracle().use { solver ->
                solver.solve(scope.build()).assignment?.valueOf(flag) shouldBe true
                scope.add(BoolNot(flag))
                solver.solve(scope.build()).status shouldBe SolveStatus.INFEASIBLE
            }
        }

        test("oracle/exact-sparse-values-beyond-long-and-optimization") {
            val scope = ConstraintScope.empty()
            val large = BigInteger.of("9223372036854775808")
            val x = scope.intVar("x", IntDomain.of(large, large + 2))
            scope.maximize(IntProduct(x, IntLiteral(2)))
            FiniteOracle().use { solver ->
                val result = solver.solve(scope.build())
                result.status shouldBe SolveStatus.OPTIMAL
                result.assignment?.valueOf(x) shouldBe large + 2
                result.objectiveValue shouldBe (large + 2) * 2
                result.bestBound shouldBe result.objectiveValue
            }
        }

        test("oracle/enumeration-deduplicates-projections-and-hides-auxiliary-variables") {
            val scope = ConstraintScope.empty()
            val x = scope.intVar("x", IntDomain.values(1, 3))
            val hidden = scope.boolVar("hidden")
            scope.project(x)
            FiniteOracle().use { solver ->
                solver.solutions(scope.build()).use { stream ->
                    val values = mutableSetOf<BigInteger?>()
                    var solutions = 0
                    while (true) {
                        when (val event = stream.next()) {
                            is SolutionFound -> {
                                solutions++
                                values += event.assignment.valueOf(x)
                                event.assignment.valueOf(hidden) shouldBe null
                            }
                            is SolveCompleted -> break
                            null -> error("Missing completion")
                        }
                    }
                    solutions shouldBe 2
                    values shouldBe setOf(BigInteger.ONE, BigInteger.of(3))
                    stream.next() shouldBe null
                }
            }
        }

        test("oracle/limits-retain-an-incumbent-without-an-optimality-claim") {
            val scope = ConstraintScope.empty()
            val x = scope.intVar("x", 1..3)
            scope.maximize(x)
            FiniteOracle().use { solver ->
                val result = solver.solve(scope.build(), SolveOptions(solutionLimit = 1))
                result.status shouldBe SolveStatus.FEASIBLE
                result.terminationReason shouldBe TerminationReason.RESOURCE_LIMIT
                result.assignment?.valueOf(x) shouldBe BigInteger.ONE
                result.bestBound shouldBe null
                val timeout = solver.solve(scope.build(), SolveOptions(timeoutMillis = 0))
                timeout.status shouldBe SolveStatus.UNKNOWN
                timeout.terminationReason shouldBe TerminationReason.TIMEOUT
            }
        }

        test("oracle/empty-domain-and-undefined-element-values-are-not-feasible") {
            val emptyScope = ConstraintScope.empty()
            emptyScope.intVar("huge", IntDomain.of(Long.MIN_VALUE, Long.MAX_VALUE))
            emptyScope.intVar("empty", IntDomain.empty())
            val scope = ConstraintScope.empty()
            val index = scope.intVar("index", -1..1)
            scope.add(IntEquality(Element(index, listOf(IntLiteral(7))), IntLiteral(7)))
            FiniteOracle().use { solver ->
                solver.solve(emptyScope.build()).status shouldBe SolveStatus.INFEASIBLE
                solver.solve(scope.build()).assignment?.valueOf(index) shouldBe BigInteger.ZERO
            }
        }

        test("oracle/rejects-unsupported-nodes-even-behind-a-false-constraint") {
            val scope = ConstraintScope.empty()
            val unknown = object : BoolExpr {}
            scope.add(BoolAnd(BoolLiteral(false), unknown))
            FiniteOracle().use { solver ->
                shouldThrow<UnsupportedModelException> { solver.solve(scope.build()) }
            }
        }

        test("oracle/rejects-large-products-before-enumerating") {
            val scope = ConstraintScope.empty()
            scope.intVar("x", IntDomain.of(Long.MIN_VALUE, Long.MAX_VALUE))
            FiniteOracle().use { solver ->
                shouldThrow<UnsupportedModelException> { solver.solve(scope.build()) }
            }
        }

        test("oracle/empty-projection-and-empty-model-each-have-one-satisfaction-solution") {
            val scope = ConstraintScope.empty()
            scope.intVar("hidden", 0..2)
            scope.project()
            FiniteOracle().use { solver ->
                solver.solutions(scope.build()).use { stream ->
                    (stream.next() is SolutionFound) shouldBe true
                    (stream.next() is SolveCompleted) shouldBe true
                    stream.next() shouldBe null
                }
                solver.solve(ConstraintScope.empty().build()).status shouldBe SolveStatus.FEASIBLE
            }
        }
    })
