package io.github.gciatto.ktconstraints.c

import io.github.gciatto.ktconstraints.core.integer.IntExpr

/** Growable list of operands for [allDifferent] and [sum], since C cannot pass Kotlin arrays. */
class IntExpressionList {
    internal val items = mutableListOf<IntExpr>()

    /** Appends [operand] and returns this list. */
    fun add(operand: IntExpression): IntExpressionList = apply { items += operand.expr }

    /** Number of operands. */
    val size: Int get() = items.size
}
