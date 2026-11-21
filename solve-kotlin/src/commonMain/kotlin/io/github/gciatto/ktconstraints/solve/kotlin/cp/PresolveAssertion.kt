package io.github.gciatto.ktconstraints.solve.kotlin.cp

import io.github.gciatto.ktconstraints.core.BoolExpr

/** One unconditionally asserted conjunct and its original assertion index. */
internal data class PresolveAssertion(
    val expression: BoolExpr,
    val sourceIndex: Int,
)
