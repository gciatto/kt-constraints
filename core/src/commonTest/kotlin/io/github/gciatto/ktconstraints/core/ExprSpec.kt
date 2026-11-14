package io.github.gciatto.ktconstraints.core

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
