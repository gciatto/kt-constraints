// Z3's Java API accepts operand lists only as varargs, so building them requires a spread copy.
@file:Suppress("SpreadOperator")

package io.github.gciatto.ktconstraints.solve.z3

import com.microsoft.z3.ArithExpr
import com.microsoft.z3.Context
import com.microsoft.z3.IntSort
import io.github.gciatto.ktconstraints.core.AllDifferent
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
import io.github.gciatto.ktconstraints.core.IntVar
import io.github.gciatto.ktconstraints.core.LinearSum
import io.github.gciatto.ktconstraints.core.Table
import io.github.gciatto.ktconstraints.core.fold
import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.solve.spi.SupportReport
import io.github.gciatto.ktconstraints.solve.spi.UnsupportedModelException
import org.gciatto.kt.math.BigInteger
import com.microsoft.z3.BoolExpr as Z3Bool
import com.microsoft.z3.Expr as Z3Expr

private typealias Z3Int = ArithExpr<IntSort>

/** Exact translation of a supported model into one owned Z3 context; confined to that context's thread. */
internal class Z3Translation(
    private val context: Context,
    model: ConstraintModel,
) {
    private val booleans = linkedMapOf<BoolVar, Z3Bool>()
    private val integers = linkedMapOf<IntVar, Z3Int>()
    private val indexDefinedness = mutableListOf<Z3Bool>()

    /** Domain, model-constraint and their element-definedness assertions. */
    val assertions: List<Z3Bool>

    init {
        val report = Z3Support.report(model)
        if (!report.isSupported) throw UnsupportedModelException(report)
        model.variables.forEachIndexed { index, variable ->
            when (variable) {
                is BoolVar -> booleans[variable] = context.mkBoolConst("b$index")
                is IntVar -> integers[variable] = context.mkIntConst("i$index")
                else -> throw UnsupportedModelException(SupportReport(listOf("Unsupported variable: ${variable.name}")))
            }
        }
        val domains = integers.map { (variable, term) -> context.domain(variable, term) }
        val constraints = model.constraints.map { boolean(it) }
        assertions = domains + constraints + drainDefinedness()
    }

    /** Returns and forgets element-index definedness assertions collected since the previous call. */
    fun drainDefinedness(): List<Z3Bool> = indexDefinedness.toList().also { indexDefinedness.clear() }

    /** Every declared Boolean variable with its Z3 term. */
    val booleanTerms: Map<BoolVar, Z3Bool> get() = booleans

    /** Every declared integer variable with its Z3 term. */
    val integerTerms: Map<IntVar, Z3Int> get() = integers

    fun boolean(expression: BoolExpr): Z3Bool = translate(expression) as Z3Bool

    @Suppress("UNCHECKED_CAST")
    fun integer(expression: IntExpr): Z3Int = translate(expression) as Z3Int

    fun literal(value: BigInteger): Z3Int = context.literal(value)

    private fun translate(expression: Expr): Z3Expr<*> =
        expression.fold { node, children: List<Z3Expr<*>> ->
            when (node) {
                is IntExpr -> integerNode(node, children)
                is BoolExpr -> booleanNode(node, children)
                else -> throw UnsupportedModelException(SupportReport(listOf("Unsupported expression: $node")))
            }
        }

    @Suppress("UNCHECKED_CAST")
    private fun integerNode(
        node: IntExpr,
        children: List<Z3Expr<*>>,
    ): Z3Int {
        val ints = children.map { it as Z3Int }
        return when (node) {
            is IntVar -> integers.getValue(node)
            is IntLiteral -> literal(node.value)
            is IntSum -> context.mkAdd(*ints.toTypedArray())
            is IntDifference -> context.mkSub(*ints.toTypedArray())
            is IntProduct -> context.mkMul(*ints.toTypedArray())
            is IntNegation -> context.mkUnaryMinus(ints.single())
            is LinearSum -> linear(node, ints)
            is Element -> element(ints)
            else -> throw UnsupportedModelException(SupportReport(listOf("Unsupported integer expression: $node")))
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun booleanNode(
        node: BoolExpr,
        children: List<Z3Expr<*>>,
    ): Z3Bool {
        val bools = { children.map { it as Z3Bool } }
        val ints = { children.map { it as Z3Int } }
        return when (node) {
            is BoolVar -> booleans.getValue(node)
            is BoolLiteral -> context.mkBool(node.value)
            is BoolAnd -> context.mkAnd(*bools().toTypedArray())
            is BoolOr -> context.mkOr(*bools().toTypedArray())
            is BoolImplies -> context.mkImplies(bools()[0], bools()[1])
            is BoolNot -> context.mkNot(bools().single())
            is BoolEquality, is IntEquality -> context.mkEq(children[0], children[1])
            is IntLessThan -> context.mkLt(ints()[0], ints()[1])
            is IntLessOrEqual -> context.mkLe(ints()[0], ints()[1])
            else -> context.global(node, children)
        }
    }

    private fun linear(
        node: LinearSum,
        terms: List<Z3Int>,
    ): Z3Int =
        context.mkAdd(
            literal(node.constant),
            *terms.zip(node.coefficients) { term, coefficient -> context.mkMul(literal(coefficient), term) }.toTypedArray(),
        )

    @Suppress("UNCHECKED_CAST")
    private fun element(children: List<Z3Int>): Z3Int {
        val index = children.first()
        val values = children.drop(1)
        indexDefinedness += context.mkAnd(context.mkGe(index, context.mkInt(0)), context.mkLt(index, context.mkInt(values.size)))
        return values.dropLast(1).foldRightIndexed(values.last()) { position, value, otherwise ->
            context.mkITE(context.mkEq(index, context.mkInt(position)), value, otherwise) as Z3Int
        }
    }
}

@Suppress("UNCHECKED_CAST")
private fun Context.global(
    node: BoolExpr,
    children: List<Z3Expr<*>>,
): Z3Bool =
    when (node) {
        is AllDifferent -> if (children.size < 2) mkTrue() else mkDistinct(*children.map { it as Z3Int }.toTypedArray())
        is Table -> table(node, children.map { it as Z3Int })
        is BoolCardinality -> cardinality(node, children.map { it as Z3Bool })
        else -> throw UnsupportedModelException(SupportReport(listOf("Unsupported Boolean expression: $node")))
    }

private fun Context.literal(value: BigInteger): Z3Int = mkInt(value.toString())

private fun Context.table(
    node: Table,
    operands: List<Z3Int>,
): Z3Bool =
    mkOr(
        *node.tuples
            .map { row -> mkAnd(*operands.zip(row) { term, value -> mkEq(term, literal(value)) }.toTypedArray()) }
            .toTypedArray(),
    )

private fun Context.cardinality(
    node: BoolCardinality,
    operands: List<Z3Bool>,
): Z3Bool {
    val array = operands.toTypedArray()
    return when (node.relation) {
        CardinalityRelation.AT_MOST -> if (array.isEmpty()) mkTrue() else mkAtMost(array, node.count)
        CardinalityRelation.AT_LEAST ->
            if (array.isEmpty()) mkBool(node.count == 0) else mkAtLeast(array, node.count)
        CardinalityRelation.EXACTLY ->
            if (array.isEmpty()) mkBool(node.count == 0) else mkPBEq(IntArray(array.size) { 1 }, array, node.count)
    }
}

private fun Context.domain(
    variable: IntVar,
    term: Z3Int,
): Z3Bool =
    mkOr(
        *variable.domain.intervals
            .map { mkAnd(mkLe(literal(it.min), term), mkLe(term, literal(it.max))) }
            .toTypedArray(),
    )
