package io.github.gciatto.ktconstraints.solve.minizinc

import io.github.gciatto.ktconstraints.core.AllDifferent
import io.github.gciatto.ktconstraints.core.BoolAnd
import io.github.gciatto.ktconstraints.core.BoolCardinality
import io.github.gciatto.ktconstraints.core.BoolEquality
import io.github.gciatto.ktconstraints.core.BoolExpr
import io.github.gciatto.ktconstraints.core.BoolImplies
import io.github.gciatto.ktconstraints.core.BoolLiteral
import io.github.gciatto.ktconstraints.core.BoolNot
import io.github.gciatto.ktconstraints.core.BoolOr
import io.github.gciatto.ktconstraints.core.BoolToInt
import io.github.gciatto.ktconstraints.core.BoolVar
import io.github.gciatto.ktconstraints.core.CardinalityRelation
import io.github.gciatto.ktconstraints.core.Circuit
import io.github.gciatto.ktconstraints.core.Cumulative
import io.github.gciatto.ktconstraints.core.DecomposableConstraint
import io.github.gciatto.ktconstraints.core.Element
import io.github.gciatto.ktconstraints.core.Expr
import io.github.gciatto.ktconstraints.core.IntDifference
import io.github.gciatto.ktconstraints.core.IntEquality
import io.github.gciatto.ktconstraints.core.IntExpr
import io.github.gciatto.ktconstraints.core.IntInterval
import io.github.gciatto.ktconstraints.core.IntLessOrEqual
import io.github.gciatto.ktconstraints.core.IntLessThan
import io.github.gciatto.ktconstraints.core.IntLiteral
import io.github.gciatto.ktconstraints.core.IntMax
import io.github.gciatto.ktconstraints.core.IntMin
import io.github.gciatto.ktconstraints.core.IntNegation
import io.github.gciatto.ktconstraints.core.IntProduct
import io.github.gciatto.ktconstraints.core.IntSum
import io.github.gciatto.ktconstraints.core.IntVar
import io.github.gciatto.ktconstraints.core.Inverse
import io.github.gciatto.ktconstraints.core.LinearSum
import io.github.gciatto.ktconstraints.core.Table
import io.github.gciatto.ktconstraints.core.VariableId
import io.github.gciatto.ktconstraints.core.fold
import io.github.gciatto.ktconstraints.solve.spi.SupportReport
import io.github.gciatto.ktconstraints.solve.spi.UnsupportedModelException
import org.gciatto.kt.math.BigInteger

internal class MiniZincLowering(
    private val names: Map<VariableId, String>,
    private val engine: MiniZincEngine,
) {
    val includes = linkedSetOf<String>()
    val definedness = linkedSetOf<String>()
    private val maximum = engine.maximumInteger

    fun lower(expression: Expr): LoweredExpression =
        expression.fold { node, children: List<LoweredExpression> ->
            when (node) {
                is IntExpr -> integer(node, children)
                is BoolExpr -> boolean(node, children)
                else -> reject("Unsupported expression: ${node::class.simpleName}")
            }
        }

    /** A top-level constraint: structural globals use MiniZinc's natives, which cannot be reified. */
    fun constraint(expression: BoolExpr): LoweredExpression =
        when (expression) {
            is BoolAnd -> LoweredExpression("(${constraint(expression.left).text} /\\ ${constraint(expression.right).text})")
            is DecomposableConstraint -> LoweredExpression(structural(expression))
            else -> lower(expression)
        }

    private fun structural(expression: DecomposableConstraint): String {
        val operands = expression.children.map { lower(it).text }
        if (operands.isEmpty()) return "true"

        fun array(items: List<String>) = "array1d(0..${items.lastIndex}, [${items.joinToString()}])"
        return when (expression) {
            is Inverse -> {
                includes += "inverse.mzn"
                "inverse(${array(
                    operands.subList(0, expression.direct.size),
                )}, ${array(operands.subList(expression.direct.size, operands.size))})"
            }
            is Circuit -> {
                includes += "circuit.mzn"
                "circuit(${array(operands)})"
            }
            is Cumulative -> {
                includes += "cumulative.mzn"
                (expression.durations + expression.demands + expression.capacity).forEach { checkValue(it) }
                expression.starts.zip(expression.durations) { start, duration ->
                    checkValue(
                        requireNotNull(lower(start).bounds).max + duration,
                    )
                }
                "cumulative([${operands.joinToString()}], [${expression.durations.joinToString()}], " +
                    "[${expression.demands.joinToString()}], ${expression.capacity})"
            }
            else -> lower(expression.decomposition).text
        }
    }

    private fun integer(
        node: IntExpr,
        children: List<LoweredExpression>,
    ): LoweredExpression =
        when (node) {
            is IntVar -> checked(name(node.id), node.domain.min ?: BigInteger.ZERO, node.domain.max ?: BigInteger.ZERO)
            is IntLiteral -> checked(node.value.toString(), node.value, node.value)
            is IntSum -> binary(children, "+") { a, b -> IntInterval(a.min + b.min, a.max + b.max) }
            is IntDifference -> binary(children, "-") { a, b -> IntInterval(a.min - b.max, a.max - b.min) }
            is IntProduct -> {
                if (engine.isMip && children.all { requireNotNull(it.bounds).let { bounds -> bounds.min != bounds.max } }) {
                    reject("${engine.engineId} supports only affine integer multiplication")
                }
                binary(children, "*") { a, b -> product(a, b) }
            }
            is IntNegation -> {
                val bounds = requireNotNull(children.single().bounds)
                checked("(-${children.single().text})", -bounds.max, -bounds.min)
            }
            is Element -> {
                val index = children.first().text
                definedness += "($index >= 0 /\\ $index < ${node.values.size})"
                val values = children.drop(1)
                val lower = values.minOf { requireNotNull(it.bounds).min }
                val upper = values.maxOf { requireNotNull(it.bounds).max }
                checked("array1d(0..${values.lastIndex}, [${values.joinToString { it.text }}])[$index]", lower, upper)
            }
            is BoolToInt -> checked("bool2int(${children.single().text})", BigInteger.ZERO, BigInteger.ONE)
            is IntMin -> extremum("min", children) { bounds -> bounds.minOf { it } }
            is IntMax -> extremum("max", children) { bounds -> bounds.maxOf { it } }
            is LinearSum -> linear(node, children)
            else -> reject("Unsupported integer expression: ${node::class.simpleName}")
        }

    private fun boolean(
        node: BoolExpr,
        children: List<LoweredExpression>,
    ): LoweredExpression {
        val text =
            when (node) {
                is BoolVar -> name(node.id)
                is BoolLiteral -> node.value.toString()
                is BoolNot -> "(not ${children.single().text})"
                is BoolAnd -> pair(children, "/\\")
                is BoolOr -> pair(children, "\\/")
                is BoolImplies -> pair(children, "->")
                is BoolEquality, is IntEquality -> pair(children, "=")
                is IntLessThan -> pair(children, "<")
                is IntLessOrEqual -> pair(children, "<=")
                is AllDifferent -> {
                    includes += "all_different.mzn"
                    if (children.isEmpty()) "true" else "all_different([${children.joinToString { it.text }}])"
                }
                is Table -> table(node, children)
                is DecomposableConstraint -> lower(node.decomposition).text
                is BoolCardinality -> {
                    checkValue(BigInteger.of(node.count))
                    val sum = if (children.isEmpty()) "0" else "sum([${children.joinToString { "bool2int(${it.text})" }}])"
                    val relation =
                        when (node.relation) {
                            CardinalityRelation.AT_MOST -> "<="
                            CardinalityRelation.EXACTLY -> "="
                            CardinalityRelation.AT_LEAST -> ">="
                        }
                    "($sum $relation ${node.count})"
                }
                else -> reject("Unsupported Boolean expression: ${node::class.simpleName}")
            }
        return LoweredExpression(text)
    }

    private fun table(
        node: Table,
        children: List<LoweredExpression>,
    ): String {
        if (node.tuples.isEmpty()) return "false"
        if (children.isEmpty()) return "true"
        includes += "table.mzn"
        val entries =
            node.tuples
                .flatten()
                .onEach { checkValue(it) }
                .joinToString()
        return "table([${children.joinToString { it.text }}], array2d(1..${node.tuples.size}, 1..${children.size}, [$entries]))"
    }

    private fun linear(
        node: LinearSum,
        children: List<LoweredExpression>,
    ): LoweredExpression {
        checkValue(node.constant)
        var bounds = IntInterval(node.constant, node.constant)
        val parts = mutableListOf(node.constant.toString())
        for ((child, coefficient) in children.zip(node.coefficients)) {
            checkValue(coefficient)
            val term = product(requireNotNull(child.bounds), IntInterval(coefficient, coefficient))
            checked("term", term.min, term.max)
            bounds = IntInterval(bounds.min + term.min, bounds.max + term.max)
            checked("partial linear sum", bounds.min, bounds.max)
            parts += "($coefficient * ${child.text})"
        }
        return checked("(${parts.joinToString(" + ")})", bounds.min, bounds.max)
    }

    private fun binary(
        children: List<LoweredExpression>,
        operator: String,
        calculate: (IntInterval, IntInterval) -> IntInterval,
    ): LoweredExpression {
        val bounds = calculate(requireNotNull(children[0].bounds), requireNotNull(children[1].bounds))
        return checked(pair(children, operator), bounds.min, bounds.max)
    }

    /** `min`/`max` of the operands; [select] picks the extremum of a list of bounds, giving both result bounds. */
    private fun extremum(
        name: String,
        children: List<LoweredExpression>,
        select: (List<BigInteger>) -> BigInteger,
    ): LoweredExpression {
        val bounds = children.map { requireNotNull(it.bounds) }
        return checked("$name([${children.joinToString { it.text }}])", select(bounds.map { it.min }), select(bounds.map { it.max }))
    }

    private fun pair(
        children: List<LoweredExpression>,
        operator: String,
    ): String = "(${children[0].text} $operator ${children[1].text})"

    private fun product(
        left: IntInterval,
        right: IntInterval,
    ): IntInterval {
        val values = listOf(left.min * right.min, left.min * right.max, left.max * right.min, left.max * right.max)
        return IntInterval(values.min(), values.max())
    }

    private fun checked(
        text: String,
        min: BigInteger,
        max: BigInteger,
    ): LoweredExpression {
        checkValue(min)
        checkValue(max)
        return LoweredExpression(text, IntInterval(min, max))
    }

    private fun checkValue(value: BigInteger) {
        if (value < -maximum || value > maximum) reject("${engine.engineId} integer range exceeded: $value")
    }

    private fun name(id: VariableId): String = names[id] ?: reject("Foreign variable identity")

    private fun reject(message: String): Nothing = throw UnsupportedModelException(SupportReport(listOf(message)))
}
