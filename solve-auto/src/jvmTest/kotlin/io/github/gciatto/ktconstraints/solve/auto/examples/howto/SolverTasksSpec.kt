package io.github.gciatto.ktconstraints.solve.auto.examples.howto

import io.github.gciatto.ktconstraints.core.Rational
import io.github.gciatto.ktconstraints.core.bitvector.BitVecLiteral
import io.github.gciatto.ktconstraints.core.bitvector.operation.BitVecBinary
import io.github.gciatto.ktconstraints.core.bitvector.operation.BitVecComparison
import io.github.gciatto.ktconstraints.core.bitvector.operation.BitVecOperator
import io.github.gciatto.ktconstraints.core.bitvector.operation.BitVecRelation
import io.github.gciatto.ktconstraints.core.domain.IntDomain
import io.github.gciatto.ktconstraints.dsl.eq
import io.github.gciatto.ktconstraints.dsl.ge
import io.github.gciatto.ktconstraints.dsl.lt
import io.github.gciatto.ktconstraints.dsl.plus
import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.model.ConstraintScope
import io.github.gciatto.ktconstraints.solve.SolveOptions
import io.github.gciatto.ktconstraints.solve.SolverCapability
import io.github.gciatto.ktconstraints.solve.choco.ChocoStore
import io.github.gciatto.ktconstraints.solve.incremental.CheckStatus
import io.github.gciatto.ktconstraints.solve.minizinc.MiniZincBackend
import io.github.gciatto.ktconstraints.solve.minizinc.MiniZincEngine
import io.github.gciatto.ktconstraints.solve.result.SolveStatus
import io.github.gciatto.ktconstraints.solve.result.TerminationReason
import io.github.gciatto.ktconstraints.solve.spi.registry.SolverRegistry
import io.github.gciatto.ktconstraints.solve.store.Entailment
import io.github.gciatto.ktconstraints.solve.stream.SolutionFound
import io.github.gciatto.ktconstraints.solve.stream.SolveCompleted
import io.github.gciatto.ktconstraints.solve.z3.Z3Backend
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import org.gciatto.kt.math.BigInteger

/** Small JVM examples use the bundled Z3 and Choco runtimes. */
class SolverTasksSpec :
    FunSpec({
        test("select a route that supports enumeration") {
            val model = ConstraintModel.of { intVar("x", 0..2) }
            // --8<-- [start:selection]
            val registry = SolverRegistry.of(Z3Backend())
            val selection = registry.rank(model, requiredCapabilities = setOf(SolverCapability.ENUMERATION))
            val backend = selection.best.backend
            val availability = backend.availability()
            val support = backend.support(model)
            val result = backend.create().use { it.solve(model) }
            // --8<-- [end:selection]
            availability.available shouldBe true
            support.isSupported shouldBe true
            result.status shouldBe SolveStatus.FEASIBLE
        }
        test("enumerate distinct projected outputs") {
            // --8<-- [start:enumerate]
            val scope = ConstraintScope.empty()
            val x = scope.intVar("x", 0..2)
            val y = scope.intVar("y", 0..2)
            scope.add(x lt y)
            scope.project(x)
            val values = mutableListOf<BigInteger>()
            Z3Backend().create().use { solver ->
                solver.solutions(scope.build()).use { stream ->
                    while (true) {
                        when (val event = stream.next()) {
                            is SolutionFound -> values += requireNotNull(event.assignment.valueOf(x))
                            is SolveCompleted -> {
                                event.result.terminationReason shouldBe TerminationReason.COMPLETED
                                break
                            }
                            null -> error("Missing completion event")
                        }
                    }
                    stream.next() shouldBe null
                }
            }
            // --8<-- [end:enumerate]
            values.shouldContainExactlyInAnyOrder(BigInteger.ZERO, BigInteger.ONE)
        }
        test("limit enumeration and close it early") {
            val model = ConstraintModel.of { intVar("x", 0..100) }
            // --8<-- [start:cancel]
            Z3Backend().create().use { solver ->
                solver.solutions(model, SolveOptions(timeoutMillis = 1_000, solutionLimit = 2)).use { stream ->
                    val first = stream.next()
                    stream.cancel()
                    stream.terminalResult?.terminationReason shouldBe TerminationReason.CANCELLED
                    (first is SolutionFound) shouldBe true
                }
            }
            // --8<-- [end:cancel]
        }
        test("roll back assertions and check temporary assumptions") {
            // --8<-- [start:session]
            val scope = ConstraintScope.empty()
            val x = scope.intVar("x", 0..3)
            scope.add(x ge 1)
            Z3Backend().create().use { solver ->
                solver.openSession(scope.build()).use { session ->
                    val checkpoint = session.push()
                    session.add(x eq 0)
                    session.check().status shouldBe CheckStatus.UNSATISFIABLE
                    session.popTo(checkpoint)
                    val impossible = x lt 1
                    val checked = session.check(assumptions = listOf(impossible))
                    checked.status shouldBe CheckStatus.UNSATISFIABLE
                    checked.unsatCore shouldBe listOf(impossible)
                    session.check().status shouldBe CheckStatus.SATISFIABLE
                }
            }
            // --8<-- [end:session]
        }
        test("propagate domains and restore a store mark") {
            // --8<-- [start:store]
            ChocoStore().use { store ->
                val x = store.intVar("x", IntDomain.of(0..3))
                val y = store.intVar("y", IntDomain.of(0..3))
                store.constrain(x + y eq 3)
                val mark = store.mark()
                store.constrain(x eq 1)
                store.domain(y) shouldBe IntDomain.of(2..2)
                store.entails(y eq 2).value shouldBe Entailment.ENTAILED
                store.undoTo(mark)
                store.domain(y) shouldBe IntDomain.of(0..3)
                store.fork().use { child ->
                    child.constrain(x eq 0)
                    child.domain(y) shouldBe IntDomain.of(3..3)
                }
                store.domain(y) shouldBe IntDomain.of(0..3)
            }
            // --8<-- [end:store]
        }
        test("read an exact rational") {
            // --8<-- [start:real]
            val scope = ConstraintScope.empty()
            val r = scope.realVar("r")
            scope.add(r eq Rational.parse("0.1"))
            val result = Z3Backend().create().use { it.solve(scope.build()) }
            result.assignment?.valueOf(r) shouldBe Rational.of(1, 10)
            // --8<-- [end:real]
        }
        test("wrap fixed-width bit-vector arithmetic") {
            // --8<-- [start:bits]
            val scope = ConstraintScope.empty()
            val x = scope.bitVecVar("x", 8)
            val one = BitVecLiteral(BigInteger.ONE, 8)
            val zero = BitVecLiteral(BigInteger.ZERO, 8)
            val sum = BitVecBinary(BitVecOperator.ADD, x, one)
            scope.add(BitVecComparison(BitVecRelation.EQUAL, sum, zero))
            val result = Z3Backend().create().use { it.solve(scope.build()) }
            result.assignment?.valueOf(x) shouldBe BigInteger.of(255)
            // --8<-- [end:bits]
        }
        test("probe an explicitly selected MiniZinc route") {
            // --8<-- [start:minizinc]
            val model = ConstraintModel.of { intVar("x", 0..2) }
            val backend = MiniZincBackend(MiniZincEngine.GECODE, System.getenv("MINIZINC_EXECUTABLE") ?: "minizinc")
            val availability = backend.availability()
            val support = backend.support(model)
            if (availability.available && support.isSupported) {
                backend.create().use { solver ->
                    solver.solve(model).status shouldBe SolveStatus.FEASIBLE
                }
            }
            // --8<-- [end:minizinc]
            support.isSupported shouldBe true
            if (!availability.available) requireNotNull(availability.reason).isNotBlank() shouldBe true
        }
    })
