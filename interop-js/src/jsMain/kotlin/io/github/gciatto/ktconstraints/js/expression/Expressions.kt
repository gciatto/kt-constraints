@file:OptIn(ExperimentalWasmJsInterop::class)

package io.github.gciatto.ktconstraints.js.expression
import io.github.gciatto.ktconstraints.core.Rational
import io.github.gciatto.ktconstraints.core.global.counting.AllDifferent
import io.github.gciatto.ktconstraints.core.global.counting.BoolCardinality
import io.github.gciatto.ktconstraints.core.global.counting.CardinalityRelation
import io.github.gciatto.ktconstraints.core.global.extensional.Element
import io.github.gciatto.ktconstraints.core.integer.IntLiteral
import io.github.gciatto.ktconstraints.core.integer.IntToReal
import io.github.gciatto.ktconstraints.core.integer.arithmetic.LinearSum
import io.github.gciatto.ktconstraints.core.real.RealLiteral
import io.github.gciatto.ktconstraints.js.global.count
import io.github.gciatto.ktconstraints.js.platform.toBigInteger
import org.gciatto.kt.math.BigInteger
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsBigInt

/** Exact integer constant. */
@JsExport
fun constant(value: JsBigInt): IntExpression = IntExpression(IntLiteral(value.toBigInteger()))

/** Exact real constant written `p`, `p/q` or as a decimal such as `0.1`; other notations are rejected. */
@JsExport
fun real(value: String): RealExpression = RealExpression(RealLiteral(Rational.parse(value)))

/** Embeds an integer expression into the reals. */
@JsExport
fun toReal(value: IntExpression): RealExpression = RealExpression(IntToReal(value.expr))

/** Sum of [terms]; empty is zero. */
@JsExport
fun sum(terms: Array<IntExpression>): IntExpression = IntExpression(LinearSum(terms.map { it.expr }, terms.map { BigInteger.ONE }))

/** All [operands] take pairwise distinct values. */
@JsExport
fun allDifferent(operands: Array<IntExpression>): BoolExpression = BoolExpression(AllDifferent(operands.map { it.expr }))

/** The zero-based [index]-th of [values]; the index is constrained to be in range. */
@JsExport
fun element(
    index: IntExpression,
    values: Array<IntExpression>,
): IntExpression = IntExpression(Element(index.expr, values.map { it.expr }))

/** At most [count] of [operands] hold. */
@JsExport
fun atMost(
    count: Int,
    operands: Array<BoolExpression>,
): BoolExpression = cardinality(operands, count, CardinalityRelation.AT_MOST)

/** At least [count] of [operands] hold. */
@JsExport
fun atLeast(
    count: Int,
    operands: Array<BoolExpression>,
): BoolExpression = cardinality(operands, count, CardinalityRelation.AT_LEAST)

/** Exactly [count] of [operands] hold. */
@JsExport
fun exactly(
    count: Int,
    operands: Array<BoolExpression>,
): BoolExpression = cardinality(operands, count, CardinalityRelation.EXACTLY)

private fun cardinality(
    operands: Array<BoolExpression>,
    count: Int,
    relation: CardinalityRelation,
) = BoolExpression(BoolCardinality(operands.map { it.expr }, count, relation))
