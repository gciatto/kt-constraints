@file:OptIn(ExperimentalForeignApi::class)

package io.github.gciatto.ktconstraints.solve.z3

import io.github.gciatto.ktconstraints.core.AllDifferent
import io.github.gciatto.ktconstraints.core.BoolLiteral
import io.github.gciatto.ktconstraints.core.Element
import io.github.gciatto.ktconstraints.core.IntDomain
import io.github.gciatto.ktconstraints.core.IntEquality
import io.github.gciatto.ktconstraints.core.IntLiteral
import io.github.gciatto.ktconstraints.core.IntVar
import io.github.gciatto.ktconstraints.core.LinearSum
import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.model.ConstraintScope
import io.github.gciatto.ktconstraints.solve.CheckStatus
import io.github.gciatto.ktconstraints.solve.ConstraintSolver
import io.github.gciatto.ktconstraints.solve.SolutionFound
import io.github.gciatto.ktconstraints.solve.SolveCompleted
import io.github.gciatto.ktconstraints.solve.SolveOptions
import io.github.gciatto.ktconstraints.solve.SolveStatus
import io.github.gciatto.ktconstraints.solve.SolverCapability
import io.github.gciatto.ktconstraints.solve.TerminationReason
import io.github.gciatto.ktconstraints.solve.spi.BackendUnavailableException
import io.github.gciatto.ktconstraints.solve.spi.SatisfiabilityStore
import io.github.gciatto.ktconstraints.test.solve.FiniteOracle
import io.github.gciatto.ktconstraints.test.solve.incrementalContract
import io.github.gciatto.ktconstraints.test.solve.solverContract
import io.github.gciatto.ktconstraints.test.solve.storeContract
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import org.gciatto.kt.math.BigInteger
import platform.posix.getenv

/** Z3 C API loaded at run time: truthful unavailability, shared contracts, exact integers, oracle differential and interruption. */
class Z3NativeSpec :
    FunSpec({
        test("z3-native/missing-library-is-reported-unavailable") {
            val backend = Z3NativeBackend("/nonexistent/kt-constraints/libz3-missing")
            val availability = backend.availability()
            availability.available shouldBe false
            availability.reason.shouldNotBeNull()
            shouldThrow<BackendUnavailableException> { backend.create() }
        }

        // Gradle provisions the pinned library for the host's native test task; other runs only check unavailability.
        if (!getenv("KT_CONSTRAINTS_Z3_LIBRARY")?.toKString().isNullOrEmpty()) {

            include(solverContract { Z3NativeBackend().create() })
            include(incrementalContract { Z3NativeBackend().create() })
            include(storeContract(propagates = false) { SatisfiabilityStore.of(Z3NativeBackend().create()) })
            include(z3OptimizationContract { Z3NativeBackend().create() })
            include(z3RealContract { Z3NativeBackend().create() })
            include(z3BitVecContract { Z3NativeBackend().create() })

            test("z3-native/route-identifies-the-c-binding") {
                val backend = Z3NativeBackend()
                backend.availability().available shouldBe true
                backend.descriptor.id shouldBe "z3/native"
                backend.descriptor.capabilities.containsAll(
                    setOf(
                        SolverCapability.INCREMENTAL_ASSERTIONS,
                        SolverCapability.ASSUMPTIONS,
                        SolverCapability.UNSAT_CORES,
                        SolverCapability.REAL_ARITHMETIC,
                        SolverCapability.BITVECTORS,
                    ),
                ) shouldBe true
                backend.create().use { it.solve(ConstraintScope.empty().build()).descriptor shouldBe backend.descriptor }
            }

            test("z3-native/integers-beyond-64-bits-are-exact") {
                val huge = BigInteger.of("1000000000000000000000000000001")
                val scope = ConstraintScope.empty()
                val x = scope.intVar("x", IntDomain.of(-huge, huge))
                scope.add(
                    IntEquality(LinearSum(listOf(x), listOf(BigInteger.TWO), BigInteger.ONE), IntLiteral(huge + huge + BigInteger.ONE)),
                )
                Z3NativeBackend().create().use { solver ->
                    val result = solver.solve(scope.build())
                    result.status shouldBe SolveStatus.FEASIBLE
                    result.assignment?.valueOf(x) shouldBe huge
                }
            }

            test("z3-native/element-definedness-matches-the-oracle") {
                val scope = ConstraintScope.empty()
                val index = scope.intVar("i", -1..4)
                val value = scope.intVar("v", -2..3)
                scope.add(IntEquality(Element(index, listOf(IntLiteral(-2), value, IntLiteral(3))), value))
                val model = scope.build()
                answers(Z3NativeBackend().create(), model, index, value) shouldBe answers(FiniteOracle(), model, index, value)
            }

            test("z3-native/owned-deadline-stops-a-hard-check") {
                Z3NativeBackend().create().use { solver ->
                    val result = solver.solve(pigeonhole(), SolveOptions(timeoutMillis = 200))
                    result.status shouldBe SolveStatus.UNKNOWN
                    result.terminationReason shouldBe TerminationReason.TIMEOUT
                }
            }

            test("z3-native/session-stays-usable-after-a-cancelled-check") {
                Z3NativeBackend().create().use { solver ->
                    solver.openSession(pigeonhole()).use { session ->
                        withTimeoutOrNull(300) { session.check() } shouldBe null
                        session.check(listOf(BoolLiteral(false))).status shouldBe CheckStatus.UNSATISFIABLE
                    }
                }
            }

            test("z3-native/session-close-interrupts-an-active-check") {
                val solver = Z3NativeBackend().create()
                val session = solver.openSession(pigeonhole())
                coroutineScope {
                    val check = async { session.check() }
                    delay(300)
                    solver.close()
                    val result = check.await()
                    result.status shouldBe CheckStatus.UNKNOWN
                    result.terminationReason shouldBe TerminationReason.CANCELLED
                }
            }
        }
    })

private fun pigeonhole(holes: Int = 12): ConstraintModel {
    val scope = ConstraintScope.empty()
    scope.add(AllDifferent((0..holes).map { scope.intVar("p$it", 0 until holes) }))
    return scope.build()
}

private suspend fun answers(
    solver: ConstraintSolver,
    model: ConstraintModel,
    first: IntVar,
    second: IntVar,
): Set<List<BigInteger?>> =
    solver.use {
        val values = mutableSetOf<List<BigInteger?>>()
        solver.solutions(model).use { stream ->
            var event = stream.next()
            while (event is SolutionFound) {
                values += listOf(event.assignment.valueOf(first), event.assignment.valueOf(second))
                event = stream.next()
            }
            check(event is SolveCompleted && event.result.terminationReason == TerminationReason.COMPLETED) { "Missing completion" }
        }
        values
    }
