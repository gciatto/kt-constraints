package io.github.gciatto.ktconstraints.js

import io.github.gciatto.ktconstraints.core.IntMax
import io.github.gciatto.ktconstraints.core.IntMin

/** The smallest of the non-empty [operands]. */
@JsExport
fun min(operands: Array<IntExpression>): IntExpression = IntExpression(IntMin(operands.map { it.expr }))

/** The largest of the non-empty [operands]. */
@JsExport
fun max(operands: Array<IntExpression>): IntExpression = IntExpression(IntMax(operands.map { it.expr }))
