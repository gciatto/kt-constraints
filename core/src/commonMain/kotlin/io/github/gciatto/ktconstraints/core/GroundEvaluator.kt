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
            else -> throw IllegalArgumentException("Unsupported Boolean expression: $node")
        }
}
