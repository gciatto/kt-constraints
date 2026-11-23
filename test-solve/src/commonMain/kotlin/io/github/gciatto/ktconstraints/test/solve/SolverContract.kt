package io.github.gciatto.ktconstraints.test.solve

import io.github.gciatto.ktconstraints.core.bool.BoolAnd
import io.github.gciatto.ktconstraints.core.bool.BoolExpr
import io.github.gciatto.ktconstraints.core.bool.BoolLiteral
import io.github.gciatto.ktconstraints.core.evaluation.GroundEvaluator
import io.github.gciatto.ktconstraints.core.global.counting.AllDifferent
import io.github.gciatto.ktconstraints.core.global.counting.BoolCardinality
import io.github.gciatto.ktconstraints.core.global.counting.CardinalityRelation
import io.github.gciatto.ktconstraints.core.global.extensional.Element
import io.github.gciatto.ktconstraints.core.global.extensional.Table
import io.github.gciatto.ktconstraints.core.integer.IntLiteral
import io.github.gciatto.ktconstraints.core.integer.arithmetic.LinearSum
import io.github.gciatto.ktconstraints.core.integer.relation.IntEquality
import io.github.gciatto.ktconstraints.model.ConstraintScope
import io.github.gciatto.ktconstraints.solve.ConstraintSolver
import io.github.gciatto.ktconstraints.solve.SolveOptions
import io.github.gciatto.ktconstraints.solve.result.SolveStatus
import io.github.gciatto.ktconstraints.solve.result.TerminationReason
import io.github.gciatto.ktconstraints.solve.spi.exception.UnsupportedModelException
import io.github.gciatto.ktconstraints.solve.stream.SolutionFound
import io.github.gciatto.ktconstraints.solve.stream.SolveCompleted
import io.github.gciatto.ktconstraints.test.solve.oracle.FiniteOracle
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.funSpec
import io.kotest.datatest.withTests
import io.kotest.matchers.shouldBe
import org.gciatto.kt.math.BigInteger

/** Shared SAT/UNSAT, optimization, enumeration and lifecycle contracts; every leaf owns fresh fixtures. */
fun solverContract(
    enumeration: Boolean = true,
    create: () -> ConstraintSolver,
) = funSpec {
    test("solver-contract/satisfiable-assignment-is-independently-validated") {
        val scope = ConstraintScope.empty()
        val x = scope.intVar("x", 0..3)
        scope.add(IntEquality(x, IntLiteral(2)))
        create().use { solver ->
            val result = solver.solve(scope.build())
            result.status shouldBe SolveStatus.FEASIBLE
            val assignment = requireNotNull(result.assignment)
            GroundEvaluator.evaluate(scope.build().constraints.single(), assignment) shouldBe true
            assignment.valueOf(x) shouldBe BigInteger.TWO
        }
    }
    test("solver-contract/unsatisfiable-is-completed-without-an-assignment") {
        val scope = ConstraintScope.empty()
        val x = scope.intVar("x", 0..1)
        scope.add(IntEquality(x, IntLiteral(2)))
        create().use { solver ->
            val result = solver.solve(scope.build())
            result.status shouldBe SolveStatus.INFEASIBLE
            result.terminationReason shouldBe TerminationReason.COMPLETED
            result.assignment shouldBe null
        }
    }
    withTests(nameFn = { "solver-contract/optimization/maximize=$it" }, listOf(false, true)) { maximize ->
        val scope = ConstraintScope.empty()
        val x = scope.intVar("x", -2..3)
        if (maximize) scope.maximize(x) else scope.minimize(x)
        create().use { solver ->
            val result = solver.solve(scope.build())
            result.status shouldBe SolveStatus.OPTIMAL
            result.objectiveValue shouldBe BigInteger.of(if (maximize) 3 else -2)
            result.assignment?.valueOf(x) shouldBe result.objectiveValue
        }
    }
    test("solver-contract/enumeration-is-unique-and-valid").config(enabled = enumeration) {
        val scope = ConstraintScope.empty()
        val x = scope.intVar("x", 0..2)
        val flag = scope.boolVar("flag")
        scope.add(IntEquality(x, IntLiteral(1)))
        val answers = mutableListOf<Boolean?>()
        create().use { solver ->
            solver.solutions(scope.build()).use { stream ->
                while (true) {
                    when (val event = stream.next()) {
                        is SolutionFound -> {
                            GroundEvaluator.evaluate(scope.build().constraints.single(), event.assignment) shouldBe true
                            answers += event.assignment.valueOf(flag)
                        }
                        is SolveCompleted -> break
                        null -> error("Missing terminal event")
                    }
                }
                stream.next() shouldBe null
            }
        }
        answers.size shouldBe 2
        answers.toSet() shouldBe setOf(false, true)
    }
    test("solver-contract/output-projection-hides-variables-and-deduplicates-solutions").config(enabled = enumeration) {
        val scope = ConstraintScope.empty()
        val x = scope.intVar("x", 0..2)
        val hidden = scope.boolVar("hidden")
        scope.project(x)
        val answers = mutableListOf<BigInteger?>()
        create().use { solver ->
            solver.solutions(scope.build()).use { stream ->
                while (true) {
                    when (val event = stream.next()) {
                        is SolutionFound -> {
                            event.assignment.valueOf(hidden) shouldBe null
                            answers += event.assignment.valueOf(x)
                        }
                        is SolveCompleted -> break
                        null -> error("Missing completion")
                    }
                }
            }
        }
        answers.size shouldBe 3
        answers.toSet() shouldBe setOf(BigInteger.ZERO, BigInteger.ONE, BigInteger.TWO)
    }
    test("solver-contract/globals-and-projection-match-the-independent-oracle") {
        val scope = ConstraintScope.empty()
        val x = scope.intVar("x", 0..2)
        val y = scope.intVar("y", 0..2)
        val flag = scope.boolVar("flag")
        scope.add(AllDifferent(listOf(x, y)))
        scope.add(Table(listOf(x, y), listOf(listOf(BigInteger.ZERO, BigInteger.ONE), listOf(BigInteger.ONE, BigInteger.TWO))))
        scope.add(IntEquality(Element(y, listOf(IntLiteral(3), IntLiteral(3), IntLiteral(3))), IntLiteral(3)))
        scope.add(IntEquality(LinearSum(listOf(x, y), listOf(BigInteger.ONE, BigInteger.of(-1))), IntLiteral(-1)))
        scope.add(BoolCardinality(listOf(flag), 1, CardinalityRelation.EXACTLY))
        scope.maximize(x)
        scope.project(x)
        val model = scope.build()
        val expected = FiniteOracle().use { it.solve(model) }
        create().use { solver ->
            val result = solver.solve(model)
            result.status shouldBe expected.status
            result.objectiveValue shouldBe expected.objectiveValue
            result.assignment?.valueOf(x) shouldBe expected.assignment?.valueOf(x)
            result.assignment?.valueOf(y) shouldBe null
            result.assignment?.valueOf(flag) shouldBe null
        }
    }
    test("solver-contract/zero-timeout-is-unknown-and-not-infeasible") {
        create().use { solver ->
            val result = solver.solve(ConstraintScope.empty().build(), SolveOptions(timeoutMillis = 0))
            result.status shouldBe SolveStatus.UNKNOWN
            result.terminationReason shouldBe TerminationReason.TIMEOUT
        }
    }
    test("solver-contract/cancel-before-first-read-and-close-are-idempotent") {
        val solver = create()
        val stream = solver.solutions(ConstraintScope.empty().build())
        stream.cancel()
        stream.close()
        (stream.next() is SolveCompleted) shouldBe true
        stream.terminalResult?.terminationReason shouldBe TerminationReason.CANCELLED
        stream.next() shouldBe null
        solver.close()
        solver.close()
        shouldThrow<IllegalStateException> { solver.solutions(ConstraintScope.empty().build()) }
    }
    test("solver-contract/limit-does-not-invent-optimality") {
        val scope = ConstraintScope.empty()
        val x = scope.intVar("x", 0..3)
        scope.maximize(x)
        create().use { solver ->
            val result = solver.solve(scope.build(), SolveOptions(solutionLimit = 1))
            result.status shouldBe SolveStatus.FEASIBLE
            result.terminationReason shouldBe TerminationReason.RESOURCE_LIMIT
        }
    }
    test("solver-contract/unknown-nodes-are-rejected-before-execution") {
        val scope = ConstraintScope.empty()
        scope.add(BoolAnd(BoolLiteral(false), object : BoolExpr {}))
        create().use { solver -> shouldThrow<UnsupportedModelException> { solver.solve(scope.build()) } }
    }
}
