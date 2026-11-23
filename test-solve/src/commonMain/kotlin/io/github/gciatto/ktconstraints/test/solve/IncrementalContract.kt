package io.github.gciatto.ktconstraints.test.solve

import io.github.gciatto.ktconstraints.core.VariableFactory
import io.github.gciatto.ktconstraints.core.bool.BoolExpr
import io.github.gciatto.ktconstraints.core.domain.IntDomain
import io.github.gciatto.ktconstraints.core.evaluation.GroundEvaluator
import io.github.gciatto.ktconstraints.core.global.extensional.Element
import io.github.gciatto.ktconstraints.core.integer.IntLiteral
import io.github.gciatto.ktconstraints.core.integer.relation.IntEquality
import io.github.gciatto.ktconstraints.core.integer.relation.IntLessOrEqual
import io.github.gciatto.ktconstraints.core.integer.relation.IntLessThan
import io.github.gciatto.ktconstraints.model.ConstraintScope
import io.github.gciatto.ktconstraints.solve.incremental.CheckStatus
import io.github.gciatto.ktconstraints.solve.incremental.IncrementalConstraintSolver
import io.github.gciatto.ktconstraints.solve.result.TerminationReason
import io.github.gciatto.ktconstraints.solve.spi.exception.UnsupportedModelException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.funSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import org.gciatto.kt.math.BigInteger

/** Shared incremental-session contracts: frames, checkpoints, assumptions and lifecycle; fresh fixtures per leaf. */
fun incrementalContract(
    unsatCores: Boolean = true,
    create: () -> IncrementalConstraintSolver,
) = funSpec {
    test("incremental-contract/repeated-checks-see-added-constraints") {
        val scope = ConstraintScope.empty()
        val x = scope.intVar("x", 0..3)
        create().use { solver ->
            solver.openSession(scope.build()).use { session ->
                session.add(IntEquality(x, IntLiteral(1)))
                val first = session.check()
                first.status shouldBe CheckStatus.SATISFIABLE
                first.assignment?.valueOf(x) shouldBe BigInteger.ONE
                session.add(IntEquality(x, IntLiteral(2)))
                val second = session.check()
                second.status shouldBe CheckStatus.UNSATISFIABLE
                second.terminationReason shouldBe TerminationReason.COMPLETED
                second.assignment shouldBe null
            }
        }
    }

    test("incremental-contract/model-constraints-form-the-root-frame") {
        val scope = ConstraintScope.empty()
        val x = scope.intVar("x", 0..3)
        scope.add(IntLessThan(x, IntLiteral(1)))
        create().use { solver ->
            solver.openSession(scope.build()).use { session ->
                session.check().assignment?.valueOf(x) shouldBe BigInteger.ZERO
                session.add(IntEquality(x, IntLiteral(2)))
                session.check().status shouldBe CheckStatus.UNSATISFIABLE
            }
        }
    }

    test("incremental-contract/nested-frames-restore-earlier-assertions") {
        val scope = ConstraintScope.empty()
        val x = scope.intVar("x", 0..3)
        val y = scope.intVar("y", 0..3)
        create().use { solver ->
            solver.openSession(scope.build()).use { session ->
                session.add(IntLessThan(x, y))
                val outer = session.push()
                session.add(IntEquality(y, IntLiteral(0)))
                session.check().status shouldBe CheckStatus.UNSATISFIABLE
                val inner = session.push()
                session.add(IntEquality(x, IntLiteral(3)))
                session.popTo(outer)
                val restored = session.check()
                restored.status shouldBe CheckStatus.SATISFIABLE
                val assignment = requireNotNull(restored.assignment)
                GroundEvaluator.evaluate(IntLessThan(x, y), assignment) shouldBe true
                shouldThrow<IllegalArgumentException> { session.popTo(inner) }
                shouldThrow<IllegalArgumentException> { session.popTo(outer) }
            }
        }
    }

    test("incremental-contract/foreign-checkpoints-are-rejected") {
        val scope = ConstraintScope.empty()
        val x = scope.intVar("x", 0..3)
        create().use { solver ->
            solver.openSession(scope.build()).use { first ->
                solver.openSession(scope.build()).use { second ->
                    val foreign = second.push()
                    first.push()
                    first.add(IntEquality(x, IntLiteral(5)))
                    shouldThrow<IllegalArgumentException> { first.popTo(foreign) }
                    first.check().status shouldBe CheckStatus.UNSATISFIABLE
                    second.check().status shouldBe CheckStatus.SATISFIABLE
                }
            }
        }
    }

    test("incremental-contract/assumptions-hold-for-one-check") {
        val scope = ConstraintScope.empty()
        val x = scope.intVar("x", 0..3)
        val one = IntEquality(x, IntLiteral(1))
        val two = IntEquality(x, IntLiteral(2))
        create().use { solver ->
            solver.openSession(scope.build()).use { session ->
                session.check(listOf(one)).assignment?.valueOf(x) shouldBe BigInteger.ONE
                val conflict = session.check(listOf(one, two))
                conflict.status shouldBe CheckStatus.UNSATISFIABLE
                conflict.assignment shouldBe null
                session.check().status shouldBe CheckStatus.SATISFIABLE
            }
        }
    }

    test("incremental-contract/unsat-core-is-a-subset-of-the-assumptions").config(enabled = unsatCores) {
        val scope = ConstraintScope.empty()
        val x = scope.intVar("x", 0..3)
        val y = scope.intVar("y", 0..3)
        val assumptions: List<BoolExpr> =
            listOf(IntEquality(y, IntLiteral(0)), IntEquality(x, IntLiteral(1)), IntEquality(x, IntLiteral(2)))
        create().use { solver ->
            solver.openSession(scope.build()).use { session ->
                val core = requireNotNull(session.check(assumptions).unsatCore)
                core.shouldNotBeEmpty()
                assumptions.containsAll(core) shouldBe true
            }
        }
    }

    test("incremental-contract/assumed-element-index-must-be-defined") {
        val scope = ConstraintScope.empty()
        val index = scope.intVar("i", -1..3)
        val assumption = IntEquality(Element(index, listOf(IntLiteral(4), IntLiteral(4))), IntLiteral(4))
        create().use { solver ->
            solver.openSession(scope.build()).use { session ->
                val result = session.check(listOf(assumption))
                result.status shouldBe CheckStatus.SATISFIABLE
                setOf(BigInteger.ZERO, BigInteger.ONE).contains(result.assignment?.valueOf(index)) shouldBe true
            }
        }
    }

    test("incremental-contract/declared-variables-join-the-session-with-their-domains") {
        val scope = ConstraintScope.empty()
        val x = scope.intVar("x", 0..3)
        val y = VariableFactory.empty().intVar("y", IntDomain.values(2, 7))
        create().use { solver ->
            solver.openSession(scope.build()).use { session ->
                session.declare(y)
                session.add(IntLessThan(x, y))
                session.add(IntLessThan(IntLiteral(2), y))
                val result = session.check()
                result.status shouldBe CheckStatus.SATISFIABLE
                result.assignment?.valueOf(y) shouldBe BigInteger.of(7)
                shouldThrow<IllegalArgumentException> { session.declare(y) }
                shouldThrow<IllegalArgumentException> { session.declare(x) }
            }
        }
    }

    test("incremental-contract/popping-a-frame-forgets-its-declarations") {
        val scope = ConstraintScope.empty()
        val x = scope.intVar("x", 0..3)
        val y = VariableFactory.empty().intVar("y", IntDomain.of(0, 1))
        create().use { solver ->
            solver.openSession(scope.build()).use { session ->
                val checkpoint = session.push()
                session.declare(y)
                session.add(IntLessOrEqual(x, y))
                session.check().status shouldBe CheckStatus.SATISFIABLE
                session.popTo(checkpoint)
                shouldThrow<IllegalArgumentException> { session.add(IntEquality(y, IntLiteral(0))) }
                session.check().assignment?.valueOf(y) shouldBe null
                session.declare(y)
                session.add(IntEquality(y, IntLiteral(1)))
                session.check().assignment?.valueOf(y) shouldBe BigInteger.ONE
            }
        }
    }

    test("incremental-contract/foreign-variables-and-unknown-nodes-change-nothing") {
        val scope = ConstraintScope.empty()
        scope.intVar("x", 0..3)
        val other = ConstraintScope.empty()
        val foreign = other.intVar("x", 0..3)
        create().use { solver ->
            solver.openSession(scope.build()).use { session ->
                shouldThrow<IllegalArgumentException> { session.add(IntEquality(foreign, IntLiteral(9))) }
                shouldThrow<UnsupportedModelException> { session.add(object : BoolExpr {}) }
                shouldThrow<IllegalArgumentException> { session.check(listOf(IntEquality(foreign, IntLiteral(9)))) }
                session.check().status shouldBe CheckStatus.SATISFIABLE
            }
        }
    }

    test("incremental-contract/zero-timeout-is-unknown") {
        create().use { solver ->
            solver.openSession(ConstraintScope.empty().build()).use { session ->
                val result = session.check(timeoutMillis = 0)
                result.status shouldBe CheckStatus.UNKNOWN
                result.terminationReason shouldBe TerminationReason.TIMEOUT
            }
        }
    }

    test("incremental-contract/closing-the-solver-closes-its-sessions") {
        val solver = create()
        val session = solver.openSession(ConstraintScope.empty().build())
        solver.close()
        session.close()
        shouldThrow<IllegalStateException> { session.push() }
        shouldThrow<IllegalStateException> { session.check() }
        shouldThrow<IllegalStateException> { solver.openSession(ConstraintScope.empty().build()) }
    }
}
