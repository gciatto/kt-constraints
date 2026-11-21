package io.github.gciatto.ktconstraints.js

/** Outcome of [Store.constrain]. */
@JsExport
class StorePropagation internal constructor(
    /** `FAILED`, `SATISFIABLE`, `CONSISTENT` (propagation found no failure) or `UNKNOWN` (never a failure). */
    val status: String,
    /** Integer variables whose domains shrank; always empty on stores that do not propagate. */
    val changed: Array<IntExpression>,
)
