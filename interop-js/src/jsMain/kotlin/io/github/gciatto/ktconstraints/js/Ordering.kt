package io.github.gciatto.ktconstraints.js

import io.github.gciatto.ktconstraints.dsl.decreasing as dslDecreasing
import io.github.gciatto.ktconstraints.dsl.increasing as dslIncreasing
import io.github.gciatto.ktconstraints.dsl.lexLess as dslLexLess
import io.github.gciatto.ktconstraints.dsl.lexLessOrEqual as dslLexLessOrEqual

/** Each operand is at most the next. */
@JsExport
fun increasing(operands: Array<IntExpression>): BoolExpression = BoolExpression(dslIncreasing(operands.map { it.expr }))

/** Each operand is at least the next. */
@JsExport
fun decreasing(operands: Array<IntExpression>): BoolExpression = BoolExpression(dslDecreasing(operands.map { it.expr }))

/** [left] precedes [right] lexicographically. */
@JsExport
fun lexLess(
    left: Array<IntExpression>,
    right: Array<IntExpression>,
): BoolExpression = BoolExpression(dslLexLess(left.map { it.expr }, right.map { it.expr }))

/** [left] precedes or equals [right] lexicographically. */
@JsExport
fun lexLessOrEqual(
    left: Array<IntExpression>,
    right: Array<IntExpression>,
): BoolExpression = BoolExpression(dslLexLessOrEqual(left.map { it.expr }, right.map { it.expr }))
