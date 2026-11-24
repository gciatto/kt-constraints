package io.github.gciatto.ktconstraints.test.solve

import io.github.gciatto.ktconstraints.core.Rational
import io.github.gciatto.ktconstraints.core.bool.BoolAnd
import io.github.gciatto.ktconstraints.core.bool.BoolExpr
import io.github.gciatto.ktconstraints.core.bool.BoolLiteral
import io.github.gciatto.ktconstraints.core.bool.BoolNot
import io.github.gciatto.ktconstraints.core.domain.IntDomain
import io.github.gciatto.ktconstraints.core.global.extensional.Element
import io.github.gciatto.ktconstraints.core.integer.IntLiteral
import io.github.gciatto.ktconstraints.core.integer.arithmetic.IntProduct
import io.github.gciatto.ktconstraints.core.integer.relation.IntEquality
import io.github.gciatto.ktconstraints.core.real.RealLiteral
import io.github.gciatto.ktconstraints.core.real.relation.RealLessThan
import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.model.ConstraintScope
import io.github.gciatto.ktconstraints.solve.SolveOptions
import io.github.gciatto.ktconstraints.solve.result.SolveStatus
import io.github.gciatto.ktconstraints.solve.result.TerminationReason
import io.github.gciatto.ktconstraints.solve.spi.exception.UnsupportedModelException
import io.github.gciatto.ktconstraints.solve.stream.SolutionFound
import io.github.gciatto.ktconstraints.solve.stream.SolveCompleted
import io.github.gciatto.ktconstraints.test.solve.oracle.FiniteOracle
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.gciatto.kt.math.BigInteger

/** The test-only finite oracle is exact, bounded and honest about incomplete search. */
class FiniteOracleSpec :
    FunSpec({
        include(solverContract { FiniteOracle() })

        test("oracle/variable-cap-also-bounds-fixed-domain-models") {
            val model =
                ConstraintModel.of {
                    repeat(FiniteOracle.MAX_VARIABLES + 1) { intVar("x$it", 0..0) }
                }
            FiniteOracle().use { solver ->
                shouldThrow<UnsupportedModelException> { solver.solve(model) }
            }
        }
        test("oracle/sat-and-unsat-with-boolean-variables") {
            with(ConstraintScope.empty()) {
                val flag = boolVar("flag")
                add(flag)
                FiniteOracle().use { solver ->
                    solver.solve(build()).assignment?.valueOf(flag) shouldBe true
                    add(BoolNot(flag))
                    solver.solve(build()).status shouldBe SolveStatus.INFEASIBLE
                }
            }
        }

        test("oracle/exact-sparse-values-beyond-long-and-optimization") {
            with(ConstraintScope.empty()) {
                val large = BigInteger.of("9223372036854775808")
                val x = intVar("x", IntDomain.of(large, large + 2))
                maximize(IntProduct(x, IntLiteral(2)))
                FiniteOracle().use { solver ->
                    val result = solver.solve(build())
                    result.status shouldBe SolveStatus.OPTIMAL
                    result.assignment?.valueOf(x) shouldBe large + 2
                    result.objectiveValue shouldBe (large + 2) * 2
                    result.bestBound shouldBe result.objectiveValue
                }
            }
        }

        test("oracle/enumeration-deduplicates-projections-and-hides-auxiliary-variables") {
            with(ConstraintScope.empty()) {
                val x = intVar("x", IntDomain.values(1, 3))
                val hidden = boolVar("hidden")
                project(x)
                FiniteOracle().use { solver ->
                    solver.solutions(build()).use { stream ->
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
        }

        test("oracle/limits-retain-an-incumbent-without-an-optimality-claim") {
            with(ConstraintScope.empty()) {
                val x = intVar("x", 1..3)
                maximize(x)
                FiniteOracle().use { solver ->
                    val result = solver.solve(build(), SolveOptions(solutionLimit = 1))
                    result.status shouldBe SolveStatus.FEASIBLE
                    result.terminationReason shouldBe TerminationReason.RESOURCE_LIMIT
                    result.assignment?.valueOf(x) shouldBe BigInteger.ONE
                    result.bestBound shouldBe null
                    val timeout = solver.solve(build(), SolveOptions(timeoutMillis = 0))
                    timeout.status shouldBe SolveStatus.UNKNOWN
                    timeout.terminationReason shouldBe TerminationReason.TIMEOUT
                }
            }
        }

        test("oracle/empty-domain-and-undefined-element-values-are-not-feasible") {
            val emptyScopeModel =
                ConstraintModel.of {
                    intVar("huge", IntDomain.of(Long.MIN_VALUE, Long.MAX_VALUE))
                    intVar("empty", IntDomain.empty())
                }
            with(ConstraintScope.empty()) {
                val index = intVar("index", -1..1)
                add(IntEquality(Element(index, listOf(IntLiteral(7))), IntLiteral(7)))
                FiniteOracle().use { solver ->
                    solver.solve(emptyScopeModel).status shouldBe SolveStatus.INFEASIBLE
                    solver.solve(build()).assignment?.valueOf(index) shouldBe BigInteger.ZERO
                }
            }
        }

        test("oracle/rejects-unsupported-nodes-even-behind-a-false-constraint") {
            val model =
                ConstraintModel.of {
                    val unknown = object : BoolExpr {}
                    add(BoolAnd(BoolLiteral(false), unknown))
                }
            FiniteOracle().use { solver ->
                shouldThrow<UnsupportedModelException> { solver.solve(model) }
            }
        }

        test("oracle/rejects-real-variables-even-when-unconstrained") {
            val declaredModel =
                ConstraintModel.of {
                    realVar("x")
                }
            val model =
                ConstraintModel.of {
                    add(RealLessThan(realVar("y"), RealLiteral(Rational.ONE)))
                }
            FiniteOracle().use { solver ->
                shouldThrow<UnsupportedModelException> { solver.solve(declaredModel) }
                shouldThrow<UnsupportedModelException> { solver.solve(model) }
            }
        }

        test("oracle/rejects-bit-vector-variables") {
            val model =
                ConstraintModel.of {
                    bitVecVar("b", 8)
                }
            FiniteOracle().use { solver -> shouldThrow<UnsupportedModelException> { solver.solve(model) } }
        }

        test("oracle/rejects-large-products-before-enumerating") {
            val model =
                ConstraintModel.of {
                    intVar("x", IntDomain.of(Long.MIN_VALUE, Long.MAX_VALUE))
                }
            FiniteOracle().use { solver ->
                shouldThrow<UnsupportedModelException> { solver.solve(model) }
            }
        }

        test("oracle/empty-projection-and-empty-model-each-have-one-satisfaction-solution") {
            val model =
                ConstraintModel.of {
                    intVar("hidden", 0..2)
                    project()
                }
            FiniteOracle().use { solver ->
                solver.solutions(model).use { stream ->
                    (stream.next() is SolutionFound) shouldBe true
                    (stream.next() is SolveCompleted) shouldBe true
                    stream.next() shouldBe null
                }
                solver.solve(ConstraintScope.empty().build()).status shouldBe SolveStatus.FEASIBLE
            }
        }
    })
