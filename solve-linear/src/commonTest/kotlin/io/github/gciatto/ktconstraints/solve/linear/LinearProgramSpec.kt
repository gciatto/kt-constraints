package io.github.gciatto.ktconstraints.solve.linear

import io.github.gciatto.ktconstraints.core.Rational
import io.github.gciatto.ktconstraints.core.bool.BoolOr
import io.github.gciatto.ktconstraints.core.domain.IntDomain
import io.github.gciatto.ktconstraints.core.integer.arithmetic.IntProduct
import io.github.gciatto.ktconstraints.core.integer.relation.IntLessThan
import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.solve.spi.exception.UnsupportedModelException
import io.github.gciatto.ktconstraints.test.solve.LINEAR_CASES
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withTests
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith

/** Exact translation of the linear fragment, exact readings of floating-point values, and exact solution checks. */
class LinearProgramSpec :
    FunSpec({
        withTests(nameFn = { "linear/translates/${it.name}" }, LINEAR_CASES) { case ->
            val model = ConstraintModel.of(case.build)
            LinearProgram.report(model).isSupported shouldBe true
            val program = LinearProgram.of(model)
            program.columns.map { it.variable } shouldBe model.variables
            program.objective?.real shouldBe true
        }

        test("linear/a-known-vertex-is-feasible-and-scores-exactly") {
            val model = ConstraintModel.of(LINEAR_CASES.single { it.name == "lp/fractional-vertex" }.build)
            val program = LinearProgram.of(model)
            val vertex = LinearSolution(program, listOf(Rational.of(4, 3), Rational.of(4, 3)))
            vertex.isFeasible shouldBe true
            vertex.objectiveValue shouldBe Rational.of(8, 3)
            val outside = LinearSolution(program, listOf(Rational.of(2), Rational.of(1)))
            outside.isFeasible shouldBe false
            outside.violation shouldBe Rational.ONE
        }

        test("linear/integer-strictness-is-exact") {
            val model =
                ConstraintModel.of {
                    val x = intVar("x", 0..9)
                    val y = intVar("y", 0..9)
                    add(IntLessThan(x, y))
                }
            val row = LinearProgram.of(model).rows.single()
            row.relation shouldBe LinearRelation.LESS_OR_EQUAL
            row.terms shouldBe mapOf(0 to Rational.ONE, 1 to Rational.of(-1))
            row.rhs shouldBe Rational.of(-1)
        }

        test("linear/strict-real-rows-are-marked") {
            val model =
                ConstraintModel.of {
                    val r = realVar("r")
                    add(r lt 1)
                }
            val program = LinearProgram.of(model)
            program.hasStrictRows shouldBe true
            program.hasIntegers shouldBe false
        }

        test("linear/non-linear-constructs-are-named") {
            val model =
                ConstraintModel.of {
                    val x = intVar("x", 0..3)
                    val y = intVar("y", 0..3)
                    val holes = intVar("holes", IntDomain.values(1, 3))
                    add(IntProduct(x, y) le 4)
                    add(BoolOr(x eq 1, y eq 2))
                    add(holes le 3)
                }
            val unsupported = LinearProgram.report(model).unsupported
            unsupported.any { it.startsWith("Product of variables") } shouldBe true
            unsupported.any { it.startsWith("Non-linear constraint: BoolOr") } shouldBe true
            unsupported.any { it.startsWith("Integer domain with holes: holes") } shouldBe true
            shouldThrow<UnsupportedModelException> { LinearProgram.of(model) }
        }

        test("linear/doubles-are-read-exactly") {
            exactRational(0.5) shouldBe Rational.of(1, 2)
            exactRational(-2.5) shouldBe Rational.of(-5, 2)
            exactRational(3.0) shouldBe Rational.of(3)
            exactRational(0.1).toString() shouldBe "3602879701896397/36028797018963968"
            exactRational(Double.MIN_VALUE).toString() shouldStartWith "1/"
            shouldThrow<IllegalArgumentException> { exactRational(Double.NaN) }
        }

        test("linear/integer-columns-round-only-within-the-tolerance") {
            val model =
                ConstraintModel.of {
                    val x = intVar("x", 0..9)
                    val r = realVar("r")
                    add(r le 9)
                    add(x ge 0)
                }
            val program = LinearProgram.of(model)
            val solution = LinearSolution.ofDoubles(program, listOf(2.9999999999, 0.25), 1e-6)
            solution.values shouldContainExactly listOf(Rational.of(3), Rational.of(1, 4))
            solution.isFeasible shouldBe true
            shouldThrow<IllegalStateException> { LinearSolution.ofDoubles(program, listOf(2.5, 0.0), 1e-6) }
        }
    })
