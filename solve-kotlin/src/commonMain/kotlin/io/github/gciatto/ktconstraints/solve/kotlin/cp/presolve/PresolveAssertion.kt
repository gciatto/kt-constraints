package io.github.gciatto.ktconstraints.solve.kotlin.cp.presolve
import io.github.gciatto.ktconstraints.core.bool.BoolExpr

/** One unconditionally asserted conjunct and its original assertion index. */
internal data class PresolveAssertion(
    val expression: BoolExpr,
    val sourceIndex: Int,
)
