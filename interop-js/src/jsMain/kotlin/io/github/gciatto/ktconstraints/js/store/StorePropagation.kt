package io.github.gciatto.ktconstraints.js.store

import io.github.gciatto.ktconstraints.js.expression.IntExpression

/** Outcome of [Store.constrain]. */
@JsExport
class StorePropagation internal constructor(
    /** `FAILED`, `SATISFIABLE`, `CONSISTENT` (propagation found no failure) or `UNKNOWN` (never a failure). */
    val status: String,
    /** Integer variables whose domains shrank; always empty on stores that do not propagate. */
    val changed: Array<IntExpression>,
)
