package io.github.gciatto.ktconstraints.solve

import io.github.gciatto.ktconstraints.core.IntDomain
import io.github.gciatto.ktconstraints.core.VariableFactory
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.gciatto.kt.math.BigInteger

/** Assignments preserve exact values, domain membership and variable identity. */
class AssignmentSpec :
    FunSpec({
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
    })
