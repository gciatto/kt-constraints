package io.github.gciatto.ktconstraints.solve.kotlin

import io.github.gciatto.ktconstraints.core.BoolExpr

/** A removable assertion is activated through an assumption, including its partial-expression definedness. */
internal class SessionAssertion(
    val expression: BoolExpr,
) {
    var literal: Int? = null
}
