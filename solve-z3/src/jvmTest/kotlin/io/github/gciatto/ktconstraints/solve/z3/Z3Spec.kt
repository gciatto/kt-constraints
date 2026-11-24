package io.github.gciatto.ktconstraints.solve.z3

import io.github.gciatto.ktconstraints.core.Variable
import io.github.gciatto.ktconstraints.core.bool.BoolExpr
import io.github.gciatto.ktconstraints.core.bool.BoolLiteral
import io.github.gciatto.ktconstraints.core.bool.BoolOr
import io.github.gciatto.ktconstraints.core.bool.BoolVar
import io.github.gciatto.ktconstraints.core.domain.IntDomain
import io.github.gciatto.ktconstraints.core.global.counting.AllDifferent
import io.github.gciatto.ktconstraints.core.global.counting.BoolCardinality
import io.github.gciatto.ktconstraints.core.global.counting.CardinalityRelation
import io.github.gciatto.ktconstraints.core.global.extensional.Element
import io.github.gciatto.ktconstraints.core.global.extensional.Table
import io.github.gciatto.ktconstraints.core.integer.IntLiteral
import io.github.gciatto.ktconstraints.core.integer.IntVar
import io.github.gciatto.ktconstraints.core.integer.arithmetic.IntProduct
import io.github.gciatto.ktconstraints.core.integer.arithmetic.LinearSum
import io.github.gciatto.ktconstraints.core.integer.relation.IntEquality
import io.github.gciatto.ktconstraints.core.integer.relation.IntLessThan
import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.model.ConstraintScope
import io.github.gciatto.ktconstraints.solve.ConstraintSolver
import io.github.gciatto.ktconstraints.solve.SolveOptions
import io.github.gciatto.ktconstraints.solve.SolverCapability
import io.github.gciatto.ktconstraints.solve.incremental.CheckStatus
import io.github.gciatto.ktconstraints.solve.result.SolveStatus
import io.github.gciatto.ktconstraints.solve.result.TerminationReason
import io.github.gciatto.ktconstraints.solve.spi.store.SatisfiabilityStore
import io.github.gciatto.ktconstraints.solve.stream.SolutionFound
import io.github.gciatto.ktconstraints.solve.stream.SolveCompleted
import io.github.gciatto.ktconstraints.test.solve.differentialContract
import io.github.gciatto.ktconstraints.test.solve.incrementalContract
import io.github.gciatto.ktconstraints.test.solve.oracle.FiniteOracle
import io.github.gciatto.ktconstraints.test.solve.solverContract
import io.github.gciatto.ktconstraints.test.solve.storeContract
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withTests
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import org.gciatto.kt.math.BigInteger
import kotlin.time.TimeSource

/** Direct Z3 JVM route: shared contracts, exact arithmetic, oracle differentials and resource release. */
class Z3Spec :
    FunSpec({
        include(solverContract { Z3Backend().createSolver() })
        include(incrementalContract { Z3Backend().createSolver() })
        include(storeContract(propagates = false) { SatisfiabilityStore.of(Z3Backend().createSolver()) })
        include(z3OptimizationContract { Z3Backend().createSolver() })
        include(z3RealContract { Z3Backend().createSolver() })
        include(z3BitVecContract { Z3Backend().createSolver() })
        include(differentialContract("z3") { Z3Backend().createSolver() })

        test("z3/runtime-is-available-and-identifies-the-direct-route") {
            val backend = Z3Backend()
            backend.availability().available shouldBe true
            backend.descriptor.id shouldBe "z3/direct"
            backend.descriptor.capabilities.containsAll(
                setOf(
                    SolverCapability.INCREMENTAL_ASSERTIONS,
                    SolverCapability.ASSUMPTIONS,
                    SolverCapability.UNSAT_CORES,
                    SolverCapability.REAL_ARITHMETIC,
                    SolverCapability.BITVECTORS,
                ),
            ) shouldBe true
            (SolverCapability.DOMAIN_PROPAGATION in backend.descriptor.capabilities) shouldBe false
        }

        test("z3/integers-beyond-64-bits-are-exact") {
            val huge = BigInteger.of("1000000000000000000000000000001")
            ConstraintScope.empty {
                val x = intVar("x", IntDomain.of(-huge, huge))
                add(IntEquality(LinearSum(listOf(x), listOf(BigInteger.TWO), BigInteger.ONE), IntLiteral(huge + huge + BigInteger.ONE)))
                Z3Backend().createSolver().use { solver ->
                    val result = solver.solve(buildModel())
                    result.status shouldBe SolveStatus.FEASIBLE
                    result.assignment?.valueOf(x) shouldBe huge
                }
            }
        }

        test("z3/unknown-nodes-are-reported-unsupported") {
            val model =
                ConstraintModel.of {
                    add(object : BoolExpr {})
                }
            val report = Z3Backend().support(model)
            report.isSupported shouldBe false
        }

        test("z3/globals-declare-lossless-decompositions") {
            val model =
                ConstraintModel.of {
                    val x = intVar("x", 0..2)
                    val y = intVar("y", 0..2)
                    add(AllDifferent(listOf(x, y)))
                }
            val report = Z3Backend().support(model)
            report.isSupported shouldBe true
            report.losslessDecompositions shouldContain "allDifferent -> distinct"
        }

        withTests(
            nameFn = { "z3/differential/${it.name}" },
            DifferentialCase("element-index-definedness") {
                val index = intVar("i", -1..4)
                val value = intVar("v", 0..9)
                add(IntEquality(Element(index, listOf(IntLiteral(5), value, IntLiteral(7))), value))
                listOf(index, value)
            },
            DifferentialCase("nonlinear-product") {
                val x = intVar("x", -4..4)
                val y = intVar("y", -4..4)
                add(IntEquality(IntProduct(x, y), IntLiteral(-4)))
                listOf(x, y)
            },
            DifferentialCase("table-or-cardinality") {
                val x = intVar("x", 0..3)
                val a = boolVar("a")
                val b = boolVar("b")
                add(
                    BoolOr(
                        Table(listOf(x), listOf(listOf(BigInteger.ONE), listOf(BigInteger.of(3)))),
                        BoolCardinality(listOf(a, b), 2, CardinalityRelation.EXACTLY),
                    ),
                )
                add(BoolCardinality(listOf(a, b), 1, CardinalityRelation.AT_LEAST))
                listOf(x, a, b)
            },
            DifferentialCase("sparse-domains-and-order") {
                val x = intVar("x", IntDomain.values(-3, 0, 4, 9))
                val y = intVar("y", IntDomain.values(-3, 4))
                add(IntLessThan(y, x))
                listOf(x, y)
            },
        ) { case ->
            ConstraintScope.empty {
                val outputs = case.build(this)
                val model = buildModel()
                answers(Z3Backend().createSolver(), model, outputs) shouldBe answers(FiniteOracle(), model, outputs)
            }
        }

        test("z3/owned-deadline-stops-a-hard-check") {
            val started = TimeSource.Monotonic.markNow()
            Z3Backend().createSolver().use { solver ->
                val result = solver.solve(pigeonhole(), SolveOptions(timeoutMillis = 200))
                result.status shouldBe SolveStatus.UNKNOWN
                result.terminationReason shouldBe TerminationReason.TIMEOUT
            }
            started.elapsedNow().inWholeMilliseconds shouldBeLessThan 10_000L
        }

        test("z3/close-interrupts-an-active-check") {
            val started = TimeSource.Monotonic.markNow()
            val solver = Z3Backend().createSolver()
            val stream = solver.solutions(pigeonhole())
            coroutineScope {
                val read = async { stream.next() }
                delay(300)
                solver.close()
                (read.await() as SolveCompleted).result.terminationReason shouldBe TerminationReason.CANCELLED
            }
            started.elapsedNow().inWholeMilliseconds shouldBeLessThan 10_000L
        }

        test("z3/session-deadline-is-unknown-not-unsatisfiable") {
            Z3Backend().createSolver().use { solver ->
                solver.openSession(pigeonhole()).use { session ->
                    val result = session.check(timeoutMillis = 200)
                    result.status shouldBe CheckStatus.UNKNOWN
                    result.terminationReason shouldBe TerminationReason.TIMEOUT
                    session.check(listOf(BoolLiteral(false))).status shouldBe CheckStatus.UNSATISFIABLE
                }
            }
        }

        test("z3/session-close-interrupts-an-active-check") {
            val started = TimeSource.Monotonic.markNow()
            val solver = Z3Backend().createSolver()
            val session = solver.openSession(pigeonhole())
            coroutineScope {
                val check = async { session.check() }
                delay(300)
                solver.close()
                val result = check.await()
                result.status shouldBe CheckStatus.UNKNOWN
                result.terminationReason shouldBe TerminationReason.CANCELLED
            }
            started.elapsedNow().inWholeMilliseconds shouldBeLessThan 10_000L
        }

        test("z3/session-stays-usable-after-a-cancelled-check") {
            Z3Backend().createSolver().use { solver ->
                solver.openSession(pigeonhole()).use { session ->
                    withTimeoutOrNull(300) { session.check() } shouldBe null
                    session.check(listOf(BoolLiteral(false))).status shouldBe CheckStatus.UNSATISFIABLE
                    session.check(listOf(BoolLiteral(true)), timeoutMillis = 100).terminationReason shouldBe TerminationReason.TIMEOUT
                }
            }
        }

        test("z3/repeated-frames-stay-consistent") {
            ConstraintScope.empty {
                val x = intVar("x", 0..1_000)
                Z3Backend().createSolver().use { solver ->
                    solver.openSession(buildModel()).use { session ->
                        repeat(200) { round ->
                            val checkpoint = session.push()
                            session.add(IntEquality(x, IntLiteral(round.toLong())))
                            session.check().assignment?.valueOf(x) shouldBe BigInteger.of(round.toLong())
                            session.add(IntLessThan(x, IntLiteral(round.toLong())))
                            session.check().status shouldBe CheckStatus.UNSATISFIABLE
                            session.popTo(checkpoint)
                        }
                        session.check().status shouldBe CheckStatus.SATISFIABLE
                    }
                }
            }
        }
    })

private class DifferentialCase(
    val name: String,
    val build: ConstraintScope.() -> List<Variable>,
) {
    override fun toString(): String = name
}

private fun pigeonhole(holes: Int = 12): ConstraintModel =
    ConstraintModel.of {
        val pigeons = (0..holes).map { intVar("p$it", 0 until holes) }
        add(AllDifferent(pigeons))
    }

@Suppress("RedundantSuspendModifier")
private suspend fun answers(
    solver: ConstraintSolver,
    model: ConstraintModel,
    outputs: List<Variable>,
): Set<List<Any?>> =
    solver.use {
        val values = mutableSetOf<List<Any?>>()
        solver.solutions(model).use { stream ->
            var event = stream.next()
            while (event is SolutionFound) {
                values +=
                    outputs.map { variable ->
                        when (variable) {
                            is BoolVar -> event.assignment.valueOf(variable)
                            is IntVar -> event.assignment.valueOf(variable)
                            else -> error("Unsupported output")
                        }
                    }
                event = stream.next()
            }
            check(event is SolveCompleted && event.result.terminationReason == TerminationReason.COMPLETED) { "Missing completion" }
        }
        values
    }
