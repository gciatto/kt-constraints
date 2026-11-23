package io.github.gciatto.ktconstraints.js.global
import io.github.gciatto.ktconstraints.core.integer.arithmetic.IntMax
import io.github.gciatto.ktconstraints.core.integer.arithmetic.IntMin
import io.github.gciatto.ktconstraints.core.integer.arithmetic.IntNegation
import io.github.gciatto.ktconstraints.js.expression.IntExpression

/** The smallest of the non-empty [operands]. */
@JsExport
fun min(operands: Array<IntExpression>): IntExpression = IntExpression(IntMin(operands.map { it.expr }))

/** The largest of the non-empty [operands]. */
@JsExport
fun max(operands: Array<IntExpression>): IntExpression = IntExpression(IntMax(operands.map { it.expr }))

/** The absolute value of [operand], as `max(operand, -operand)`. */
@JsExport
fun abs(operand: IntExpression): IntExpression = IntExpression(IntMax(listOf(operand.expr, IntNegation(operand.expr))))
