package io.github.gciatto.ktconstraints.solve

import io.github.gciatto.ktconstraints.core.Rational
import io.github.gciatto.ktconstraints.core.VariableFactory
import io.github.gciatto.ktconstraints.core.domain.IntDomain
import io.github.gciatto.ktconstraints.solve.assignment.Assignment
import io.github.gciatto.ktconstraints.solve.assignment.project
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.gciatto.kt.math.BigInteger

/** Assignments preserve exact values, domain membership and variable identity. */
class AssignmentSpec :
    FunSpec({
        test("assignment/projection-requires-values-and-hides-other-variables") {
            val factory = VariableFactory.empty()
            val x = factory.intVar("x", IntDomain.of(0, 2))
            val flag = factory.boolVar("flag")
            val assignment = Assignment.of(mapOf(flag to true), mapOf(x to BigInteger.ONE))
            val projected = assignment.project(listOf(x))
            projected.valueOf(x) shouldBe BigInteger.ONE
            projected.valueOf(flag) shouldBe null
            shouldThrow<IllegalArgumentException> { projected.project(listOf(flag)) }
        }
        test("assignment/machine-integer-reads-are-exact-or-fail") {
            val factory = VariableFactory.empty()
            val small = factory.intVar("small", IntDomain.of(-5, 5))
            val wide = factory.intVar("wide", IntDomain.of(0, Long.MAX_VALUE))
            val huge = factory.intVar("huge", IntDomain.of(BigInteger.ZERO, BigInteger.of("9223372036854775808")))
            val unset = factory.intVar("unset", IntDomain.of(0, 1))
            val assignment =
                Assignment.of(
                    integers =
                        mapOf(
                            small to BigInteger.of(-3),
                            wide to BigInteger.of(Long.MAX_VALUE),
                            huge to BigInteger.of("9223372036854775808"),
                        ),
                )
            assignment.intValueOf(small) shouldBe -3
            assignment.longValueOf(small) shouldBe -3L
            assignment.longValueOf(wide) shouldBe Long.MAX_VALUE
            assignment.intValueOf(unset) shouldBe null
            assignment.longValueOf(unset) shouldBe null
            shouldThrow<ArithmeticException> { assignment.intValueOf(wide) }
            shouldThrow<ArithmeticException> { assignment.longValueOf(huge) }
        }
        test("assignment/snapshots-typed-input-maps") {
            val factory = VariableFactory.empty()
            val flag = factory.boolVar("flag")
            val value = BigInteger.of("9223372036854775808")
            val x = factory.intVar("x", IntDomain.of(BigInteger.ZERO, value))
            val booleans = mutableMapOf(flag to true)
            val integers = mutableMapOf(x to value)
            val assignment = Assignment.of(booleans, integers)
            booleans.clear()
            integers.clear()
            assignment.valueOf(flag) shouldBe true
            assignment.valueOf(x) shouldBe value
        }

        test("assignment/unassigned-and-foreign-variables-remain-unassigned") {
            val factory = VariableFactory.empty()
            val x = factory.intVar("x", IntDomain.of(0, 1))
            val foreign = VariableFactory.empty().intVar("x", IntDomain.of(0, 1))
            val flag = factory.boolVar("flag")
            val assignment = Assignment.of(integers = mapOf(x to BigInteger.ONE))
            assignment.valueOf(foreign) shouldBe null
            assignment.valueOf(flag) shouldBe null
            Assignment.empty().valueOf(x) shouldBe null
        }

        test("assignment/rejects-out-of-domain-values-including-sparse-holes") {
            val factory = VariableFactory.empty()
            val x = factory.intVar("x", IntDomain.values(1, 3))
            val empty = factory.intVar("empty", IntDomain.empty())
            shouldThrow<IllegalArgumentException> { Assignment.of(integers = mapOf(x to BigInteger.TWO)) }
            shouldThrow<IllegalArgumentException> { Assignment.of(integers = mapOf(empty to BigInteger.ZERO)) }
        }

        test("assignment/real-values-are-exact-and-projected") {
            val factory = VariableFactory.empty()
            val x = factory.realVar("x")
            val y = factory.realVar("y")
            val assignment = Assignment.of(reals = mapOf(x to Rational.of(2, 3), y to Rational.of(-1, 7)))
            assignment.valueOf(x) shouldBe Rational.of(2, 3)
            val projected = assignment.project(listOf(x))
            projected.valueOf(x) shouldBe Rational.of(2, 3)
            projected.valueOf(y) shouldBe null
            Assignment.empty().valueOf(x) shouldBe null
        }

        test("assignment/bit-vector-values-are-in-range-and-projected") {
            val factory = VariableFactory.empty()
            val b = factory.bitVecVar("b", 8)
            val c = factory.bitVecVar("c", 100)
            val wide = BigInteger.TWO.pow(100) - BigInteger.ONE
            val assignment = Assignment.of(bitVectors = mapOf(b to BigInteger.of(255), c to wide))
            assignment.valueOf(c) shouldBe wide
            assignment.project(listOf(b)).valueOf(b) shouldBe BigInteger.of(255)
            assignment.project(listOf(b)).valueOf(c) shouldBe null
            shouldThrow<IllegalArgumentException> { Assignment.of(bitVectors = mapOf(b to BigInteger.of(256))) }
        }
    })
