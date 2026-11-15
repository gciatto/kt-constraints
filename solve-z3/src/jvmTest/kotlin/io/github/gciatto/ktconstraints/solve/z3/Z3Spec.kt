package io.github.gciatto.ktconstraints.solve.z3

import io.github.gciatto.ktconstraints.core.AllDifferent
import io.github.gciatto.ktconstraints.core.BoolCardinality
import io.github.gciatto.ktconstraints.core.BoolExpr
import io.github.gciatto.ktconstraints.core.BoolOr
import io.github.gciatto.ktconstraints.core.BoolVar
import io.github.gciatto.ktconstraints.core.CardinalityRelation
import io.github.gciatto.ktconstraints.core.Element
import io.github.gciatto.ktconstraints.core.IntDomain
import io.github.gciatto.ktconstraints.core.IntEquality
import io.github.gciatto.ktconstraints.core.IntLessThan
import io.github.gciatto.ktconstraints.core.IntLiteral
import io.github.gciatto.ktconstraints.core.IntProduct
import io.github.gciatto.ktconstraints.core.IntVar
import io.github.gciatto.ktconstraints.core.LinearSum
import io.github.gciatto.ktconstraints.core.Table
import io.github.gciatto.ktconstraints.core.Variable
import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.model.ConstraintScope
import io.github.gciatto.ktconstraints.solve.ConstraintSolver
import io.github.gciatto.ktconstraints.solve.SolutionFound
import io.github.gciatto.ktconstraints.solve.SolveCompleted
import io.github.gciatto.ktconstraints.solve.SolveOptions
import io.github.gciatto.ktconstraints.solve.SolveStatus
import io.github.gciatto.ktconstraints.solve.TerminationReason
import io.github.gciatto.ktconstraints.test.solve.FiniteOracle
import io.github.gciatto.ktconstraints.test.solve.solverContract
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withTests
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import org.gciatto.kt.math.BigInteger
import kotlin.time.TimeSource

/** Direct Z3 JVM route: shared contracts, exact arithmetic, oracle differentials and resource release. */
class Z3Spec :
    FunSpec({
        include(solverContract { Z3Backend().create() })

        test("z3/runtime-is-available-and-identifies-the-direct-route") {
            val backend = Z3Backend()
            backend.availability().available shouldBe true
            backend.descriptor.id shouldBe "z3/direct"
        }

        test("z3/integers-beyond-64-bits-are-exact") {
            val huge = BigInteger.of("1000000000000000000000000000001")
            val scope = ConstraintScope.empty()
            val x = scope.intVar("x", IntDomain.of(-huge, huge))
            scope.add(IntEquality(LinearSum(listOf(x), listOf(BigInteger.TWO), BigInteger.ONE), IntLiteral(huge + huge + BigInteger.ONE)))
            Z3Backend().create().use { solver ->
                val result = solver.solve(scope.build())
                result.status shouldBe SolveStatus.FEASIBLE
                result.assignment?.valueOf(x) shouldBe huge
            }
        }

        test("z3/unknown-nodes-are-reported-unsupported") {
            val scope = ConstraintScope.empty()
            scope.add(object : BoolExpr {})
            val report = Z3Backend().support(scope.build())
            report.isSupported shouldBe false
        }

        test("z3/globals-declare-lossless-decompositions") {
            val scope = ConstraintScope.empty()
            val x = scope.intVar("x", 0..2)
            val y = scope.intVar("y", 0..2)
            scope.add(AllDifferent(listOf(x, y)))
            val report = Z3Backend().support(scope.build())
            report.isSupported shouldBe true
            report.losslessDecompositions shouldContain "allDifferent -> distinct"
        }

        withTests(
            nameFn = { "z3/differential/${it.name}" },
            DifferentialCase("element-index-definedness") { scope ->
                val index = scope.intVar("i", -1..4)
                val value = scope.intVar("v", 0..9)
                scope.add(IntEquality(Element(index, listOf(IntLiteral(5), value, IntLiteral(7))), value))
                listOf(index, value)
            },
            DifferentialCase("nonlinear-product") { scope ->
                val x = scope.intVar("x", -4..4)
                val y = scope.intVar("y", -4..4)
                scope.add(IntEquality(IntProduct(x, y), IntLiteral(-4)))
                listOf(x, y)
            },
            DifferentialCase("table-or-cardinality") { scope ->
                val x = scope.intVar("x", 0..3)
                val a = scope.boolVar("a")
                val b = scope.boolVar("b")
                scope.add(
                    BoolOr(
                        Table(listOf(x), listOf(listOf(BigInteger.ONE), listOf(BigInteger.of(3)))),
                        BoolCardinality(listOf(a, b), 2, CardinalityRelation.EXACTLY),
                    ),
                )
                scope.add(BoolCardinality(listOf(a, b), 1, CardinalityRelation.AT_LEAST))
                listOf(x, a, b)
            },
            DifferentialCase("sparse-domains-and-order") { scope ->
                val x = scope.intVar("x", IntDomain.values(-3, 0, 4, 9))
                val y = scope.intVar("y", IntDomain.values(-3, 4))
                scope.add(IntLessThan(y, x))
                listOf(x, y)
            },
        ) { case ->
            val scope = ConstraintScope.empty()
            val outputs = case.build(scope)
            val model = scope.build()
            answers(Z3Backend().create(), model, outputs) shouldBe answers(FiniteOracle(), model, outputs)
        }

        test("z3/owned-deadline-stops-a-hard-check") {
            val started = TimeSource.Monotonic.markNow()
            Z3Backend().create().use { solver ->
                val result = solver.solve(pigeonhole(), SolveOptions(timeoutMillis = 200))
                result.status shouldBe SolveStatus.UNKNOWN
                result.terminationReason shouldBe TerminationReason.TIMEOUT
            }
            started.elapsedNow().inWholeMilliseconds shouldBeLessThan 10_000L
        }

        test("z3/close-interrupts-an-active-check") {
            val started = TimeSource.Monotonic.markNow()
            val solver = Z3Backend().create()
            val stream = solver.solutions(pigeonhole())
            coroutineScope {
                val read = async { stream.next() }
                delay(300)
                solver.close()
                (read.await() as SolveCompleted).result.terminationReason shouldBe TerminationReason.CANCELLED
            }
            started.elapsedNow().inWholeMilliseconds shouldBeLessThan 10_000L
        }
    })

private class DifferentialCase(
    val name: String,
    val build: (ConstraintScope) -> List<Variable>,
) {
    override fun toString(): String = name
}

private fun pigeonhole(holes: Int = 12): ConstraintModel {
    val scope = ConstraintScope.empty()
    val pigeons = (0..holes).map { scope.intVar("p$it", 0 until holes) }
    scope.add(AllDifferent(pigeons))
    return scope.build()
}

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
