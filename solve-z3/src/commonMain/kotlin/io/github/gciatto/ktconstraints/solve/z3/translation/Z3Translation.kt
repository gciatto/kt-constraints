package io.github.gciatto.ktconstraints.solve.z3.translation
import io.github.gciatto.ktconstraints.core.Expr
import io.github.gciatto.ktconstraints.core.Rational
import io.github.gciatto.ktconstraints.core.Variable
import io.github.gciatto.ktconstraints.core.VariableFactory
import io.github.gciatto.ktconstraints.core.bitvector.BitVecExpr
import io.github.gciatto.ktconstraints.core.bitvector.BitVecLiteral
import io.github.gciatto.ktconstraints.core.bitvector.BitVecToInt
import io.github.gciatto.ktconstraints.core.bitvector.BitVecVar
import io.github.gciatto.ktconstraints.core.bitvector.operation.BitVecBinary
import io.github.gciatto.ktconstraints.core.bitvector.operation.BitVecComparison
import io.github.gciatto.ktconstraints.core.bitvector.operation.BitVecNegation
import io.github.gciatto.ktconstraints.core.bitvector.operation.BitVecNot
import io.github.gciatto.ktconstraints.core.bool.BoolAnd
import io.github.gciatto.ktconstraints.core.bool.BoolEquality
import io.github.gciatto.ktconstraints.core.bool.BoolExpr
import io.github.gciatto.ktconstraints.core.bool.BoolImplies
import io.github.gciatto.ktconstraints.core.bool.BoolLiteral
import io.github.gciatto.ktconstraints.core.bool.BoolNot
import io.github.gciatto.ktconstraints.core.bool.BoolOr
import io.github.gciatto.ktconstraints.core.bool.BoolToInt
import io.github.gciatto.ktconstraints.core.bool.BoolVar
import io.github.gciatto.ktconstraints.core.evaluation.fold
import io.github.gciatto.ktconstraints.core.global.DecomposableConstraint
import io.github.gciatto.ktconstraints.core.global.counting.AllDifferent
import io.github.gciatto.ktconstraints.core.global.counting.BoolCardinality
import io.github.gciatto.ktconstraints.core.global.counting.CardinalityRelation
import io.github.gciatto.ktconstraints.core.global.extensional.Element
import io.github.gciatto.ktconstraints.core.global.extensional.Table
import io.github.gciatto.ktconstraints.core.integer.IntExpr
import io.github.gciatto.ktconstraints.core.integer.IntLiteral
import io.github.gciatto.ktconstraints.core.integer.IntToReal
import io.github.gciatto.ktconstraints.core.integer.IntVar
import io.github.gciatto.ktconstraints.core.integer.arithmetic.IntDifference
import io.github.gciatto.ktconstraints.core.integer.arithmetic.IntMax
import io.github.gciatto.ktconstraints.core.integer.arithmetic.IntMin
import io.github.gciatto.ktconstraints.core.integer.arithmetic.IntNegation
import io.github.gciatto.ktconstraints.core.integer.arithmetic.IntProduct
import io.github.gciatto.ktconstraints.core.integer.arithmetic.IntSum
import io.github.gciatto.ktconstraints.core.integer.arithmetic.LinearSum
import io.github.gciatto.ktconstraints.core.integer.relation.IntEquality
import io.github.gciatto.ktconstraints.core.integer.relation.IntLessOrEqual
import io.github.gciatto.ktconstraints.core.integer.relation.IntLessThan
import io.github.gciatto.ktconstraints.core.real.RealExpr
import io.github.gciatto.ktconstraints.core.real.RealLiteral
import io.github.gciatto.ktconstraints.core.real.RealVar
import io.github.gciatto.ktconstraints.core.real.arithmetic.RealDifference
import io.github.gciatto.ktconstraints.core.real.arithmetic.RealNegation
import io.github.gciatto.ktconstraints.core.real.arithmetic.RealScale
import io.github.gciatto.ktconstraints.core.real.arithmetic.RealSum
import io.github.gciatto.ktconstraints.core.real.relation.RealEquality
import io.github.gciatto.ktconstraints.core.real.relation.RealLessOrEqual
import io.github.gciatto.ktconstraints.core.real.relation.RealLessThan
import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.solve.assignment.Assignment
import io.github.gciatto.ktconstraints.solve.spi.exception.UnsupportedModelException
import io.github.gciatto.ktconstraints.solve.spi.support.SupportReport
import io.github.gciatto.ktconstraints.solve.z3.port.Z3Port
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
    private var constants = 0
    private val hidden = mutableSetOf<Variable>()

    /** Domain, model-constraint and their element-definedness assertions. */
    val assertions: List<T>

    init {
        val report = Z3Support.report(model)
        if (!report.isSupported) throw UnsupportedModelException(report)
        val domains = model.variables.mapNotNull { declare(it) }
        val constraints = model.constraints.map { topLevel(it) }
        assertions = domains + constraints + drainDefinedness()
    }

    /** Creates a fresh constant for [variable], returning its domain assertion when it has one. */
    fun declare(variable: Variable): T? {
        val index = constants++
        return when (variable) {
            is BoolVar -> null.also { booleans[variable] = port.boolConst("b$index") }
            is IntVar ->
                port.intConst("i$index").let {
                    integers[variable] = it
                    domain(variable, it)
                }
            is RealVar -> null.also { reals[variable] = port.realConst("r$index") }
            is BitVecVar -> null.also { bitVectors[variable] = port.bitVecConst("v$index", variable.width) }
            else -> throw UnsupportedModelException(SupportReport(listOf("Unsupported variable: ${variable.name}")))
        }
    }

    /** Translates a model constraint, using hidden-variable decompositions of top-level globals. */
    private fun topLevel(constraint: BoolExpr): T =
        when (constraint) {
            is BoolAnd -> port.and(listOf(topLevel(constraint.left), topLevel(constraint.right)))
            is DecomposableConstraint -> {
                val formula = constraint.decompose(VariableFactory.empty())
                val fresh = formula.variables.filter { it !in booleans && it !in integers }
                hidden += fresh
                port.and(fresh.mapNotNull { declare(it) } + translate(formula))
            }
            else -> translate(constraint)
        }

    /** Drops [variables] from translation and assignments; their constants are never reused. */
    fun forget(variables: Collection<Variable>) {
        booleans.keys.removeAll(variables)
        integers.keys.removeAll(variables)
        reals.keys.removeAll(variables)
        bitVectors.keys.removeAll(variables)
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
            booleans.filterKeys { it !in hidden }.mapValues { (_, term) -> port.boolValue(term) },
            integers.filterKeys { it !in hidden }.mapValues { (_, term) -> port.intValue(term) },
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
            is IntMin -> children.reduce { best, next -> port.ite(port.lessOrEqual(best, next), best, next) }
            is IntMax -> children.reduce { best, next -> port.ite(port.lessOrEqual(next, best), best, next) }
            is BoolToInt -> port.ite(children.single(), port.int(BigInteger.ONE), port.int(BigInteger.ZERO))
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
            is DecomposableConstraint -> translate(node.decomposition)
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
