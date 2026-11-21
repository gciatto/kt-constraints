package io.github.gciatto.ktconstraints.core

import org.gciatto.kt.math.BigInteger
import kotlin.jvm.JvmStatic

/** Pure exact evaluation of built-in ground expressions; never performs constraint search. */
object GroundEvaluator {
    /** Evaluates an integer expression, rejecting variables and unsupported extension nodes. */
    @JvmStatic
    fun evaluate(expression: IntExpr): BigInteger = evaluateGround(expression) as BigInteger

    /** Evaluates a Boolean expression, rejecting variables and unsupported extension nodes. */
    @JvmStatic
    fun evaluate(expression: BoolExpr): Boolean = evaluateGround(expression) as Boolean

    /** Evaluates a linear real expression exactly, rejecting variables and unsupported extension nodes. */
    @JvmStatic
    fun evaluate(expression: RealExpr): Rational = evaluateGround(expression) as Rational

    /** Evaluates a linear real expression exactly under complete variable bindings. */
    @JvmStatic
    fun evaluate(
        expression: RealExpr,
        valuation: Valuation,
    ): Rational = evaluateGround(expression, valuation) as Rational

    /** Evaluates a bit-vector expression to its unsigned value, rejecting variables and unsupported extension nodes. */
    @JvmStatic
    fun evaluate(expression: BitVecExpr): BigInteger = evaluateGround(expression) as BigInteger

    /** Evaluates a bit-vector expression to its unsigned value under complete, in-range variable bindings. */
    @JvmStatic
    fun evaluate(
        expression: BitVecExpr,
        valuation: Valuation,
    ): BigInteger = evaluateGround(expression, valuation) as BigInteger

    /** Evaluates an integer expression under complete, domain-valid variable bindings. */
    @JvmStatic
    fun evaluate(
        expression: IntExpr,
        valuation: Valuation,
    ): BigInteger = evaluateGround(expression, valuation) as BigInteger

    /** Evaluates a Boolean expression under complete, domain-valid variable bindings. */
    @JvmStatic
    fun evaluate(
        expression: BoolExpr,
        valuation: Valuation,
    ): Boolean = evaluateGround(expression, valuation) as Boolean

    private fun evaluateGround(
        expression: Expr,
        valuation: Valuation? = null,
    ): Any {
        require(valuation != null || expression.variables.isEmpty()) { "Ground evaluation does not accept variables" }
        return expression.fold { node, values: List<Any> ->
            when (node) {
                is IntVar ->
                    requireNotNull(valuation?.valueOf(node)) { "Missing integer binding: ${node.name}" }.also {
                        require(it in node.domain) { "Integer binding outside domain: ${node.name}" }
                    }
                is BoolVar -> requireNotNull(valuation?.valueOf(node)) { "Missing Boolean binding: ${node.name}" }
                is RealVar -> requireNotNull(valuation?.valueOf(node)) { "Missing real binding: ${node.name}" }
                is BitVecVar ->
                    requireNotNull(valuation?.valueOf(node)) { "Missing bit-vector binding: ${node.name}" }.also {
                        require(BitVectors.fits(it, node.width)) { "Bit-vector binding outside its width: ${node.name}" }
                    }
                is BitVecExpr -> BitVectors.evaluate(node, values)
                is RealExpr -> evaluateReal(node, values)
                is IntExpr -> evaluateInteger(node, values)
                is BoolExpr -> evaluateBoolean(node, values)
                else -> throw IllegalArgumentException("Unsupported expression: $node")
            }
        }
    }

    private fun evaluateInteger(
        node: IntExpr,
        values: List<Any>,
    ): BigInteger =
        when (node) {
            is IntLiteral -> node.value
            is IntSum -> (values[0] as BigInteger) + (values[1] as BigInteger)
            is IntDifference -> (values[0] as BigInteger) - (values[1] as BigInteger)
            is IntProduct -> (values[0] as BigInteger) * (values[1] as BigInteger)
            is IntNegation -> -(values[0] as BigInteger)
            is Element -> evaluateElement(node, values)
            is IntMin -> values.minOf { it as BigInteger }
            is IntMax -> values.maxOf { it as BigInteger }
            is BitVecToInt -> BitVectors.toInt(values[0] as BigInteger, node.operand.width, node.signed)
            is LinearSum ->
                values.zip(node.coefficients).fold(node.constant) { total, (value, coefficient) ->
                    total + (value as BigInteger) * coefficient
                }
            else -> throw IllegalArgumentException("Unsupported integer expression: $node")
        }

    private fun evaluateReal(
        node: RealExpr,
        values: List<Any>,
    ): Rational =
        when (node) {
            is RealLiteral -> node.value
            is RealSum -> (values[0] as Rational) + (values[1] as Rational)
            is RealDifference -> (values[0] as Rational) - (values[1] as Rational)
            is RealNegation -> -(values[0] as Rational)
            is RealScale -> node.coefficient * (values[0] as Rational)
            is IntToReal -> Rational.of(values[0] as BigInteger)
            else -> throw IllegalArgumentException("Unsupported real expression: $node")
        }

    private fun evaluateBoolean(
        node: BoolExpr,
        values: List<Any>,
    ): Boolean =
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
            is RealEquality -> values[0] == values[1]
            is BitVecComparison -> BitVectors.compare(node, values[0] as BigInteger, values[1] as BigInteger)
            is RealLessThan -> (values[0] as Rational) < (values[1] as Rational)
            is RealLessOrEqual -> (values[0] as Rational) <= (values[1] as Rational)
            is AllDifferent -> values.toSet().size == values.size
            is Table -> node.tuples.any { it == values }
            is BoolCardinality -> evaluateCardinality(node, values)
            else -> throw IllegalArgumentException("Unsupported Boolean expression: $node")
        }

    private fun evaluateElement(
        node: Element,
        values: List<Any>,
    ): BigInteger {
        val index = values[0] as BigInteger
        if (index < BigInteger.ZERO || index >= BigInteger.of(node.values.size)) {
            throw UndefinedExpressionException("Element index out of range: $index")
        }
        return values[index.toIntExact() + 1] as BigInteger
    }

    private fun evaluateCardinality(
        node: BoolCardinality,
        values: List<Any>,
    ): Boolean {
        val actual = values.count { it == true }
        return when (node.relation) {
            CardinalityRelation.AT_MOST -> actual <= node.count
            CardinalityRelation.EXACTLY -> actual == node.count
            CardinalityRelation.AT_LEAST -> actual >= node.count
        }
    }
}
