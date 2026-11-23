package io.github.gciatto.ktconstraints.core

import io.github.gciatto.ktconstraints.core.domain.IntDomain
import io.github.gciatto.ktconstraints.core.evaluation.fold
import io.github.gciatto.ktconstraints.core.integer.IntExpr
import io.github.gciatto.ktconstraints.core.integer.IntLiteral
import io.github.gciatto.ktconstraints.core.integer.arithmetic.IntNegation
import io.github.gciatto.ktconstraints.core.integer.arithmetic.IntProduct
import io.github.gciatto.ktconstraints.core.integer.arithmetic.IntSum
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/** Structural equality and deterministic traversal remain distinct from symbolic equality. */
class ExprSpec :
    FunSpec({
        test("fold/visits-children-before-parent-in-operand-order") {
            val expression = IntSum(IntLiteral(1), IntNegation(IntLiteral(2)))
            val order = mutableListOf<Expr>()
            val count =
                expression.fold { node, children: List<Int> ->
                    order += node
                    1 + children.sum()
                }
            count shouldBe 4
            order shouldBe listOf(IntLiteral(1), IntLiteral(2), IntNegation(IntLiteral(2)), expression)
        }

        test("fold-and-variables/handle-chains-deeper-than-the-call-stack") {
            val factory = VariableFactory.empty()
            val xs = (0 until 100).map { factory.intVar("x$it", IntDomain.of(0, 1)) }
            val chain = (1 until 100_000).fold<Int, IntExpr>(xs[0]) { total, i -> IntSum(total, xs[i % xs.size]) }
            chain.fold { _, children: List<Int> -> 1 + children.sum() } shouldBe 199_999
            chain.variables shouldBe xs.toSet()
        }

        test("expressions/preserve-variable-identity-and-operand-order") {
            val factory = VariableFactory.empty()
            val x = factory.intVar("x", IntDomain.of(0, 1))
            val y = factory.intVar("y", IntDomain.of(0, 1))
            val expression = IntProduct(x, IntNegation(y))
            expression.children shouldBe listOf(x, IntNegation(y))
            expression.variables shouldBe setOf(x, y)
            expression shouldBe IntProduct(x, IntNegation(y))
            expression.hashCode() shouldBe IntProduct(x, IntNegation(y)).hashCode()
            (expression == IntProduct(y, IntNegation(x))) shouldBe false
            expression.toString() shouldBe IntProduct(x, IntNegation(y)).toString()
        }
    })
