@file:OptIn(ExperimentalWasmJsInterop::class)

package io.github.gciatto.ktconstraints.js.global
import io.github.gciatto.ktconstraints.js.expression.BoolExpression
import io.github.gciatto.ktconstraints.js.expression.IntExpression
import io.github.gciatto.ktconstraints.js.platform.toBigInteger
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsBigInt
import io.github.gciatto.ktconstraints.dsl.among as dslAmong
import io.github.gciatto.ktconstraints.dsl.count as dslCount
import io.github.gciatto.ktconstraints.dsl.globalCardinality as dslGlobalCardinality
import io.github.gciatto.ktconstraints.dsl.toInt as dslToInt

/** 1 when [condition] holds, 0 otherwise. */
@JsExport
fun toInt(condition: BoolExpression): IntExpression = IntExpression(condition.expr.dslToInt())

/** Number of [operands] equal to [value]. */
@JsExport
fun count(
    value: IntExpression,
    operands: Array<IntExpression>,
): IntExpression = IntExpression(dslCount(value.expr, operands.map { it.expr }))

/** Number of [operands] whose value is one of [values]. */
@JsExport
fun among(
    values: Array<JsBigInt>,
    operands: Array<IntExpression>,
): IntExpression = IntExpression(dslAmong(values.map { it.toBigInteger() }, operands.map { it.expr }))

/** Each `values[k]` occurs exactly `counts[k]` times among [operands]. */
@JsExport
fun globalCardinality(
    operands: Array<IntExpression>,
    values: Array<JsBigInt>,
    counts: Array<IntExpression>,
): BoolExpression = BoolExpression(dslGlobalCardinality(operands.map { it.expr }, values.map { it.toBigInteger() }, counts.map { it.expr }))
