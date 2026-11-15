package io.github.gciatto.ktconstraints.solve.z3

import io.github.gciatto.ktconstraints.core.AllDifferent
import io.github.gciatto.ktconstraints.core.BitVecBinary
import io.github.gciatto.ktconstraints.core.BitVecComparison
import io.github.gciatto.ktconstraints.core.BitVecExpr
import io.github.gciatto.ktconstraints.core.BitVecLiteral
import io.github.gciatto.ktconstraints.core.BitVecNegation
import io.github.gciatto.ktconstraints.core.BitVecNot
import io.github.gciatto.ktconstraints.core.BitVecToInt
import io.github.gciatto.ktconstraints.core.BitVecVar
import io.github.gciatto.ktconstraints.core.BoolAnd
import io.github.gciatto.ktconstraints.core.BoolCardinality
import io.github.gciatto.ktconstraints.core.BoolEquality
import io.github.gciatto.ktconstraints.core.BoolExpr
import io.github.gciatto.ktconstraints.core.BoolImplies
import io.github.gciatto.ktconstraints.core.BoolLiteral
import io.github.gciatto.ktconstraints.core.BoolNot
import io.github.gciatto.ktconstraints.core.BoolOr
import io.github.gciatto.ktconstraints.core.BoolVar
import io.github.gciatto.ktconstraints.core.CardinalityRelation
import io.github.gciatto.ktconstraints.core.Element
import io.github.gciatto.ktconstraints.core.Expr
import io.github.gciatto.ktconstraints.core.IntDifference
import io.github.gciatto.ktconstraints.core.IntEquality
import io.github.gciatto.ktconstraints.core.IntExpr
import io.github.gciatto.ktconstraints.core.IntLessOrEqual
import io.github.gciatto.ktconstraints.core.IntLessThan
import io.github.gciatto.ktconstraints.core.IntLiteral
import io.github.gciatto.ktconstraints.core.IntNegation
import io.github.gciatto.ktconstraints.core.IntProduct
import io.github.gciatto.ktconstraints.core.IntSum
import io.github.gciatto.ktconstraints.core.IntToReal
import io.github.gciatto.ktconstraints.core.IntVar
import io.github.gciatto.ktconstraints.core.LinearSum
import io.github.gciatto.ktconstraints.core.Rational
import io.github.gciatto.ktconstraints.core.RealDifference
import io.github.gciatto.ktconstraints.core.RealEquality
import io.github.gciatto.ktconstraints.core.RealExpr
import io.github.gciatto.ktconstraints.core.RealLessOrEqual
import io.github.gciatto.ktconstraints.core.RealLessThan
import io.github.gciatto.ktconstraints.core.RealLiteral
import io.github.gciatto.ktconstraints.core.RealNegation
import io.github.gciatto.ktconstraints.core.RealScale
import io.github.gciatto.ktconstraints.core.RealSum
import io.github.gciatto.ktconstraints.core.RealVar
import io.github.gciatto.ktconstraints.core.Table
import io.github.gciatto.ktconstraints.core.fold
import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.solve.Assignment
import io.github.gciatto.ktconstraints.solve.spi.SupportReport
import io.github.gciatto.ktconstraints.solve.spi.UnsupportedModelException
import org.gciatto.kt.math.BigInteger

/** Exact translation of a supported model through one [Z3Port]; use only inside its owner's confinement. */
internal class Z3Translation<T>(
    private val port: Z3Port<T>,
    model: ConstraintModel,
) {
    private val booleans = linkedMapOf<BoolVar, T>()
    private val integers = linkedMapOf<IntVar, T>()
    private val reals = linkedMapOf<RealVar, T>()
    private val bitVectors = linkedMapOf<BitVecVar, T>()
    private val indexDefinedness = mutableListOf<T>()

    /** Domain, model-constraint and their element-definedness assertions. */
    val assertions: List<T>

    init {
        val report = Z3Support.report(model)
        if (!report.isSupported) throw UnsupportedModelException(report)
        model.variables.forEachIndexed { index, variable ->
            when (variable) {
                is BoolVar -> booleans[variable] = port.boolConst("b$index")
                is IntVar -> integers[variable] = port.intConst("i$index")
                is RealVar -> reals[variable] = port.realConst("r$index")
                is BitVecVar -> bitVectors[variable] = port.bitVecConst("v$index", variable.width)
                else -> throw UnsupportedModelException(SupportReport(listOf("Unsupported variable: ${variable.name}")))
            }
        }
        val domains = integers.map { (variable, term) -> domain(variable, term) }
        val constraints = model.constraints.map { translate(it) }
        assertions = domains + constraints + drainDefinedness()
    }

    /** Returns and forgets element-index definedness assertions collected since the previous call. */
    fun drainDefinedness(): List<T> = indexDefinedness.toList().also { indexDefinedness.clear() }

    /** Translates a Boolean or integer expression; element definedness is collected for [drainDefinedness]. */
    fun translate(expression: Expr): T =
        expression.fold { node, children: List<T> ->
            when (node) {
                is IntExpr -> integer(node, children)
                is RealExpr -> real(node, children)
                is BitVecExpr -> bitVec(node, children)
                is BoolExpr -> boolean(node, children)
                else -> throw UnsupportedModelException(SupportReport(listOf("Unsupported expression: $node")))
            }
        }

    /** Term equal to [value] for the projected [variable]. */
    fun binding(
        variable: BoolVar,
        value: Boolean,
    ): T = port.equal(booleans.getValue(variable), port.bool(value))

    /** Term equal to [value] for the projected [variable]. */
    fun binding(
        variable: IntVar,
        value: BigInteger,
    ): T = port.equal(integers.getValue(variable), port.int(value))

    /** Term equal to [value] for the projected [variable]. */
    fun binding(
        variable: RealVar,
        value: Rational,
    ): T = port.equal(reals.getValue(variable), port.real(value))

    /** Term equal to [value] for the projected [variable]. */
    fun binding(
        variable: BitVecVar,
        value: BigInteger,
    ): T = port.equal(bitVectors.getValue(variable), port.bitVec(value, variable.width))

    /** Complete assignment of every declared variable from the last satisfiable check. */
    fun assignment(): Assignment =
        Assignment.of(
            booleans.mapValues { (_, term) -> port.boolValue(term) },
            integers.mapValues { (_, term) -> port.intValue(term) },
            reals.mapValues { (_, term) -> port.realValue(term) },
            bitVectors.mapValues { (_, term) -> port.bitVecValue(term) },
        )

    private fun integer(
        node: IntExpr,
        children: List<T>,
    ): T =
        when (node) {
            is IntVar -> integers.getValue(node)
            is IntLiteral -> port.int(node.value)
            is IntSum -> port.add(children)
            is IntDifference -> port.subtract(children[0], children[1])
            is IntProduct -> port.multiply(children)
            is IntNegation -> port.negate(children.single())
            is LinearSum -> linear(node, children)
            is Element -> element(children)
            is BitVecToInt -> port.bitVecToInt(children.single(), node.signed)
            else -> throw UnsupportedModelException(SupportReport(listOf("Unsupported integer expression: $node")))
        }

    private fun real(
        node: RealExpr,
        children: List<T>,
    ): T =
        when (node) {
            is RealVar -> reals.getValue(node)
            is RealLiteral -> port.real(node.value)
            is RealSum -> port.add(children)
            is RealDifference -> port.subtract(children[0], children[1])
            is RealNegation -> port.negate(children.single())
            is RealScale -> port.multiply(listOf(port.real(node.coefficient), children.single()))
            is IntToReal -> port.toReal(children.single())
            else -> throw UnsupportedModelException(SupportReport(listOf("Unsupported real expression: $node")))
        }

    private fun bitVec(
        node: BitVecExpr,
        children: List<T>,
    ): T =
        when (node) {
            is BitVecVar -> bitVectors.getValue(node)
            is BitVecLiteral -> port.bitVec(node.value, node.width)
            is BitVecBinary -> port.bitVecBinary(node.operator, children[0], children[1])
            is BitVecNot -> port.bitVecNot(children.single())
            is BitVecNegation -> port.bitVecNegate(children.single())
            else -> throw UnsupportedModelException(SupportReport(listOf("Unsupported bit-vector expression: $node")))
        }

    private fun boolean(
        node: BoolExpr,
        children: List<T>,
    ): T =
        when (node) {
            is BoolVar -> booleans.getValue(node)
            is BoolLiteral -> port.bool(node.value)
            is BoolAnd -> port.and(children)
            is BoolOr -> port.or(children)
            is BoolImplies -> port.implies(children[0], children[1])
            is BoolNot -> port.not(children.single())
            is BoolEquality, is IntEquality, is RealEquality -> port.equal(children[0], children[1])
            is IntLessThan, is RealLessThan -> port.less(children[0], children[1])
            is IntLessOrEqual, is RealLessOrEqual -> port.lessOrEqual(children[0], children[1])
            is BitVecComparison -> port.bitVecCompare(node.relation, children[0], children[1])
            else -> global(node, children)
        }

    private fun global(
        node: BoolExpr,
        children: List<T>,
    ): T =
        when (node) {
            is AllDifferent -> if (children.size < 2) port.bool(true) else port.distinct(children)
            is Table -> port.or(node.tuples.map { row -> port.and(children.zip(row) { term, value -> port.equal(term, port.int(value)) }) })
            is BoolCardinality -> cardinality(node, children)
            else -> throw UnsupportedModelException(SupportReport(listOf("Unsupported Boolean expression: $node")))
        }

    private fun cardinality(
        node: BoolCardinality,
        operands: List<T>,
    ): T =
        if (operands.isEmpty()) {
            port.bool(node.relation == CardinalityRelation.AT_MOST || node.count == 0)
        } else {
            when (node.relation) {
                CardinalityRelation.AT_MOST -> port.atMost(operands, node.count)
                CardinalityRelation.AT_LEAST -> port.atLeast(operands, node.count)
                CardinalityRelation.EXACTLY -> port.exactly(operands, node.count)
            }
        }

    private fun linear(
        node: LinearSum,
        terms: List<T>,
    ): T =
        port.add(
            listOf(port.int(node.constant)) +
                terms.zip(node.coefficients) { term, coefficient -> port.multiply(listOf(port.int(coefficient), term)) },
        )

    private fun element(children: List<T>): T {
        val index = children.first()
        val values = children.drop(1)
        indexDefinedness +=
            port.and(listOf(port.lessOrEqual(port.int(BigInteger.ZERO), index), port.less(index, port.int(BigInteger.of(values.size)))))
        return values.dropLast(1).foldRightIndexed(values.last()) { position, value, otherwise ->
            port.ite(port.equal(index, port.int(BigInteger.of(position))), value, otherwise)
        }
    }

    private fun domain(
        variable: IntVar,
        term: T,
    ): T =
        port.or(
            variable.domain.intervals.map {
                port.and(listOf(port.lessOrEqual(port.int(it.min), term), port.lessOrEqual(term, port.int(it.max))))
            },
        )
}
