package io.github.gciatto.ktconstraints.core

import kotlin.jvm.JvmStatic
import org.gciatto.kt.math.BigInteger

/** Pure exact evaluation of built-in ground expressions; never performs constraint search. */
object GroundEvaluator {
    /** Evaluates an integer expression, rejecting variables and unsupported extension nodes. */
    @JvmStatic
    fun evaluate(expression: IntExpr): BigInteger = evaluateGround(expression) as BigInteger

    /** Evaluates a Boolean expression, rejecting variables and unsupported extension nodes. */
    @JvmStatic
    fun evaluate(expression: BoolExpr): Boolean = evaluateGround(expression) as Boolean

    private fun evaluateGround(expression: Expr): Any {
        require(expression.variables.isEmpty()) { "Ground evaluation does not accept variables" }
        return expression.fold { node, values: List<Any> ->
            when (node) {
                is IntExpr -> evaluateInteger(node, values)
                is BoolExpr -> evaluateBoolean(node, values)
                else -> throw IllegalArgumentException("Unsupported expression: $node")
            }
        }
    }

    private fun evaluateInteger(node: IntExpr, values: List<Any>): BigInteger =
        when (node) {
            is IntLiteral -> node.value
            is IntSum -> (values[0] as BigInteger) + (values[1] as BigInteger)
            is IntDifference -> (values[0] as BigInteger) - (values[1] as BigInteger)
            is IntProduct -> (values[0] as BigInteger) * (values[1] as BigInteger)
            is IntNegation -> -(values[0] as BigInteger)
            is Element -> evaluateElement(node, values)
            is LinearSum -> values.zip(node.coefficients).fold(node.constant) { total, (value, coefficient) ->
                total + (value as BigInteger) * coefficient
            }
            else -> throw IllegalArgumentException("Unsupported integer expression: $node")
        }

    private fun evaluateBoolean(node: BoolExpr, values: List<Any>): Boolean =
        when (node) {
            is BoolLiteral -> node.value
            is BoolAnd -> (values[0] as Boolean) && (values[1] as Boolean)
            is BoolOr -> (values[0] as Boolean) || (values[1] as Boolean)
            is BoolImplies -> !(values[0] as Boolean) || (values[1] as Boolean)
            is BoolNot -> !(values[0] as Boolean)
            is BoolEquality -> values[0] == values[1]
            is IntEquality -> values[0] == values[1]
            is IntLessThan -> (values[0] as BigInteger) < (values[1] as BigInteger)
            is IntLessOrEqual -> (values[0] as BigInteger) <= (values[1] as BigInteger)
            is AllDifferent -> values.toSet().size == values.size
            is Table -> node.tuples.any { it == values }
            is BoolCardinality -> evaluateCardinality(node, values)
            else -> throw IllegalArgumentException("Unsupported Boolean expression: $node")
        }

    private fun evaluateElement(node: Element, values: List<Any>): BigInteger {
        val index = values[0] as BigInteger
        require(index >= BigInteger.ZERO && index < BigInteger.of(node.values.size)) { "Element index out of range: $index" }
        return values[index.toIntExact() + 1] as BigInteger
    }

    private fun evaluateCardinality(node: BoolCardinality, values: List<Any>): Boolean {
        val actual = values.count { it == true }
        return when (node.relation) {
            CardinalityRelation.AT_MOST -> actual <= node.count
            CardinalityRelation.EXACTLY -> actual == node.count
            CardinalityRelation.AT_LEAST -> actual >= node.count
        }
    }
}
