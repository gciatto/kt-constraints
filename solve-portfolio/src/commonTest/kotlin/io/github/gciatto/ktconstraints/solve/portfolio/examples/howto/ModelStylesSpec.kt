package io.github.gciatto.ktconstraints.solve.portfolio.examples.howto

import io.github.gciatto.ktconstraints.core.integer.IntLiteral
import io.github.gciatto.ktconstraints.core.integer.arithmetic.IntSum
import io.github.gciatto.ktconstraints.core.integer.relation.IntEquality
import io.github.gciatto.ktconstraints.dsl.eq
import io.github.gciatto.ktconstraints.dsl.minimizeViolations
import io.github.gciatto.ktconstraints.dsl.plus
import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.model.ConstraintScope
import io.github.gciatto.ktconstraints.solve.SolveOptions
import io.github.gciatto.ktconstraints.solve.portfolio.solveAuto
import io.github.gciatto.ktconstraints.solve.result.SolveStatus
import io.github.gciatto.ktconstraints.solve.result.TerminationReason
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.gciatto.kt.math.BigInteger

/** Executable models shared by the programming-style, optimization and limit guides. */
class ModelStylesSpec :
    FunSpec({
        test("receiver DSL and explicit factories build equivalent constraints") {
            // --8<-- [start:receiver]
            val model =
                ConstraintModel.of {
                    val x = intVar("x", 0..3)
                    val y = intVar("y", 0..3)
                    add(x + y eq 3)
                }
            // --8<-- [end:receiver]
            // --8<-- [start:factories]
            val scope = ConstraintScope.empty()
            val x = scope.intVar("x", 0..3)
            val y = scope.intVar("y", 0..3)
            scope.add(IntEquality(IntSum(x, y), IntLiteral(3)))
            val explicit = scope.build()
            // --8<-- [end:factories]
            solveAuto(model).status shouldBe SolveStatus.FEASIBLE
            val answer = solveAuto(explicit)
            val values = requireNotNull(answer.assignment)
            (values.valueOf(x)!! + values.valueOf(y)!!) shouldBe BigInteger.of(3)
        }
        test("maximize an integer objective") {
            // --8<-- [start:optimize]
            val scope = ConstraintScope.empty()
            val x = scope.intVar("x", 0..3)
            val y = scope.intVar("y", 0..3)
            scope.add(x + y eq 3)
            scope.maximize(x)
            val result = solveAuto(scope.build())
            // --8<-- [end:optimize]
            result.status shouldBe SolveStatus.OPTIMAL
            result.objectiveValue shouldBe BigInteger.of(3)
            result.assignment?.valueOf(x) shouldBe BigInteger.of(3)
        }
        test("minimize weighted violations") {
            // --8<-- [start:soft]
            val scope = ConstraintScope.empty()
            val x = scope.intVar("x", 0..1)
            scope.minimizeViolations((x eq 1) to 3L, (x eq 0) to 1L)
            val result = solveAuto(scope.build())
            // --8<-- [end:soft]
            result.status shouldBe SolveStatus.OPTIMAL
            result.objectiveValue shouldBe BigInteger.ONE
            result.assignment?.valueOf(x) shouldBe BigInteger.ONE
        }
        test("a zero budget gives no feasibility proof") {
            // --8<-- [start:limits]
            val model = ConstraintModel.of { intVar("x", 0..3) }
            val result = solveAuto(model, SolveOptions(timeoutMillis = 0))
            // --8<-- [end:limits]
            result.status shouldBe SolveStatus.UNKNOWN
            result.terminationReason shouldBe TerminationReason.TIMEOUT
            result.assignment shouldBe null
        }
    })
