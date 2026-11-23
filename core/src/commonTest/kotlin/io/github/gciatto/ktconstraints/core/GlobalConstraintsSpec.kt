package io.github.gciatto.ktconstraints.core

import io.github.gciatto.ktconstraints.core.bool.BoolLiteral
import io.github.gciatto.ktconstraints.core.domain.IntDomain
import io.github.gciatto.ktconstraints.core.evaluation.GroundEvaluator
import io.github.gciatto.ktconstraints.core.global.counting.AllDifferent
import io.github.gciatto.ktconstraints.core.global.counting.BoolCardinality
import io.github.gciatto.ktconstraints.core.global.counting.CardinalityRelation
import io.github.gciatto.ktconstraints.core.global.extensional.Element
import io.github.gciatto.ktconstraints.core.global.extensional.Table
import io.github.gciatto.ktconstraints.core.integer.IntExpr
import io.github.gciatto.ktconstraints.core.integer.IntLiteral
import io.github.gciatto.ktconstraints.core.integer.arithmetic.IntMax
import io.github.gciatto.ktconstraints.core.integer.arithmetic.IntMin
import io.github.gciatto.ktconstraints.core.integer.arithmetic.LinearSum
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.gciatto.kt.math.BigInteger

/** Global nodes retain their semantics and immutable operands until backend lowering. */
class GlobalConstraintsSpec :
    FunSpec({
        test("allDifferent/empty-distinct-and-duplicate-values") {
            GroundEvaluator.evaluate(AllDifferent(emptyList())) shouldBe true
            GroundEvaluator.evaluate(AllDifferent(listOf(IntLiteral(1), IntLiteral(2)))) shouldBe true
            GroundEvaluator.evaluate(AllDifferent(listOf(IntLiteral(1), IntLiteral(1)))) shouldBe false
        }

        test("element/uses-zero-based-exact-index") {
            val values = listOf(IntLiteral(4), IntLiteral("9223372036854775808"))
            GroundEvaluator.evaluate(Element(IntLiteral(1), values)).toString() shouldBe "9223372036854775808"
            shouldThrow<IllegalArgumentException> { GroundEvaluator.evaluate(Element(IntLiteral(-1), values)) }
            shouldThrow<IllegalArgumentException> { GroundEvaluator.evaluate(Element(IntLiteral(2), values)) }
            shouldThrow<IllegalArgumentException> {
                GroundEvaluator.evaluate(Element(IntLiteral("9223372036854775808"), values))
            }
            shouldThrow<IllegalArgumentException> { Element(IntLiteral(0), emptyList()) }
        }

        test("table/allowed-tuples-and-zero-arity") {
            val operands = listOf(IntLiteral(1), IntLiteral(2))
            val allowed = listOf(listOf(BigInteger.ONE, BigInteger.TWO))
            GroundEvaluator.evaluate(Table(operands, allowed)) shouldBe true
            GroundEvaluator.evaluate(Table(operands.reversed(), allowed)) shouldBe false
            GroundEvaluator.evaluate(Table(emptyList(), emptyList())) shouldBe false
            GroundEvaluator.evaluate(Table(emptyList(), listOf(emptyList()))) shouldBe true
            shouldThrow<IllegalArgumentException> { Table(operands, listOf(listOf(BigInteger.ONE))) }
        }

        test("linearSum/keeps-coefficients-constant-and-exact-arithmetic") {
            val expression =
                LinearSum(
                    listOf(IntLiteral("9223372036854775808"), IntLiteral(3)),
                    listOf(BigInteger.TWO, BigInteger.of(-1)),
                    BigInteger.ONE,
                )
            GroundEvaluator.evaluate(expression).toString() shouldBe "18446744073709551614"
            GroundEvaluator.evaluate(LinearSum(emptyList(), emptyList())) shouldBe BigInteger.ZERO
            shouldThrow<IllegalArgumentException> { LinearSum(listOf(IntLiteral(1)), emptyList()) }
        }

        test("cardinality/counts-true-operands-including-empty-cases") {
            val values = listOf(BoolLiteral(true), BoolLiteral(false), BoolLiteral(true))
            GroundEvaluator.evaluate(BoolCardinality(values, 2, CardinalityRelation.EXACTLY)) shouldBe true
            GroundEvaluator.evaluate(BoolCardinality(values, 1, CardinalityRelation.AT_MOST)) shouldBe false
            GroundEvaluator.evaluate(BoolCardinality(values, 1, CardinalityRelation.AT_LEAST)) shouldBe true
            GroundEvaluator.evaluate(BoolCardinality(emptyList(), 0, CardinalityRelation.EXACTLY)) shouldBe true
            GroundEvaluator.evaluate(BoolCardinality(emptyList(), 1, CardinalityRelation.AT_LEAST)) shouldBe false
            shouldThrow<IllegalArgumentException> { BoolCardinality(values, -1, CardinalityRelation.EXACTLY) }
        }

        test("minMax/select-the-exact-extremum-and-reject-empty-operands") {
            val values = listOf(IntLiteral(4), IntLiteral("-9223372036854775809"), IntLiteral(7))
            GroundEvaluator.evaluate(IntMin(values)).toString() shouldBe "-9223372036854775809"
            GroundEvaluator.evaluate(IntMax(values)) shouldBe BigInteger.of(7)
            GroundEvaluator.evaluate(IntMin(listOf(IntLiteral(3)))) shouldBe BigInteger.of(3)
            shouldThrow<IllegalArgumentException> { IntMin(emptyList()) }
            shouldThrow<IllegalArgumentException> { IntMax(emptyList()) }
            IntMin(values) shouldBe IntMin(values.toMutableList())
            (IntMin(values) == IntMax(values)) shouldBe false
        }

        test("globals/snapshot-inputs-and-retain-structural-equality") {
            val operands = mutableListOf<IntExpr>(IntLiteral(1), IntLiteral(2))
            val row = mutableListOf(BigInteger.ONE, BigInteger.TWO)
            val rows = mutableListOf<List<BigInteger>>(row)
            val different = AllDifferent(operands)
            val table = Table(operands, rows)
            operands.clear()
            row.clear()
            rows.clear()
            runCatching { (different.operands as MutableList).clear() }
            runCatching { (table.tuples.single() as MutableList).clear() }
            different shouldBe AllDifferent(listOf(IntLiteral(1), IntLiteral(2)))
            table shouldBe Table(listOf(IntLiteral(1), IntLiteral(2)), listOf(listOf(BigInteger.ONE, BigInteger.TWO)))
            GroundEvaluator.evaluate(table) shouldBe true
        }

        test("globals/variables-include-index-and-all-operands") {
            val factory = VariableFactory.empty()
            val x = factory.intVar("x", IntDomain.of(0, 2))
            val y = factory.intVar("y", IntDomain.of(0, 2))
            Element(x, listOf(y)).variables shouldBe setOf(x, y)
            AllDifferent(listOf(x, y)).variables shouldBe setOf(x, y)
            Table(listOf(x, y), emptyList()).variables shouldBe setOf(x, y)
        }
    })
