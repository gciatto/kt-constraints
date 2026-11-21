package io.github.gciatto.ktconstraints.model

import io.github.gciatto.ktconstraints.core.BitVecComparison
import io.github.gciatto.ktconstraints.core.BitVecLiteral
import io.github.gciatto.ktconstraints.core.BitVecRelation
import io.github.gciatto.ktconstraints.core.IntDomain
import io.github.gciatto.ktconstraints.core.IntEquality
import io.github.gciatto.ktconstraints.core.IntExpr
import io.github.gciatto.ktconstraints.core.IntLiteral
import io.github.gciatto.ktconstraints.core.IntSum
import io.github.gciatto.ktconstraints.core.Rational
import io.github.gciatto.ktconstraints.core.RealLessThan
import io.github.gciatto.ktconstraints.core.RealLiteral
import io.github.gciatto.ktconstraints.core.VariableFactory
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/** Frozen models and variable ownership contract. */
class ConstraintScopeSpec :
    FunSpec({
        test("scope/snapshot-is-detached") {
            val scope = ConstraintScope.empty()
            val x = scope.intVar("x", 0..10)
            val y = scope.intVar("y", 0..10)
            scope.add(IntEquality(IntSum(x, y), IntLiteral(10)))
            val before = scope.build()
            scope.add(IntEquality(x, IntLiteral(2)))
            before.constraints.size shouldBe 1
            scope.build().constraints.size shouldBe 2
        }

        test("scope/machine-integer-bounds-declare-an-interval") {
            val scope = ConstraintScope.empty()
            scope.intVar("x", -3L, Long.MAX_VALUE).domain shouldBe IntDomain.of(-3, Long.MAX_VALUE)
            shouldThrow<IllegalArgumentException> { scope.intVar("y", 2L, 1L) }
        }
        test("scope/accepts-expressions-deeper-than-the-call-stack") {
            val scope = ConstraintScope.empty()
            val x = scope.intVar("x", 0..1)
            val chain = (1 until 100_000).fold<Int, IntExpr>(x) { total, _ -> IntSum(total, x) }
            scope.add(IntEquality(chain, IntLiteral(0)))
            scope.build().constraints.size shouldBe 1
        }

        test("scope/variable-identities-are-distinct") {
            val a = ConstraintScope.empty().intVar("x", 0..10)
            val b = ConstraintScope.empty().intVar("x", 0..10)
            (a == b) shouldBe false
        }

        test("scope/foreign-variables-are-rejected") {
            val first = ConstraintScope.empty()
            val second = ConstraintScope.empty()
            val foreign = second.intVar("foreign", 1..10)
            first.intVar("local", 1..10)
            shouldThrow<IllegalArgumentException> {
                first.add(IntEquality(foreign, IntLiteral(3)))
            }
        }

        test("scope/duplicate-variable-names-are-rejected") {
            val scope = ConstraintScope.empty()
            scope.boolVar("x")
            shouldThrow<IllegalArgumentException> { scope.intVar("x", 0..10) }
        }

        test("scope/real-variables-are-declared-and-owned") {
            val scope = ConstraintScope.empty()
            val x = scope.realVar("x")
            scope.add(RealLessThan(x, RealLiteral(Rational.of(1, 2))))
            scope.build().variables shouldBe listOf(x)
            val foreign = VariableFactory.empty().realVar("y")
            shouldThrow<IllegalArgumentException> { scope.add(RealLessThan(foreign, RealLiteral(Rational.ONE))) }
            shouldThrow<IllegalArgumentException> { scope.realVar("x") }
        }

        test("scope/bit-vector-variables-are-declared-and-owned") {
            val scope = ConstraintScope.empty()
            val b = scope.bitVecVar("b", 8)
            scope.add(BitVecComparison(BitVecRelation.UNSIGNED_LESS_THAN, b, BitVecLiteral(3, 8)))
            scope.build().variables shouldBe listOf(b)
            val foreign = VariableFactory.empty().bitVecVar("c", 8)
            shouldThrow<IllegalArgumentException> { scope.add(BitVecComparison(BitVecRelation.EQUAL, foreign, b)) }
        }
    })
