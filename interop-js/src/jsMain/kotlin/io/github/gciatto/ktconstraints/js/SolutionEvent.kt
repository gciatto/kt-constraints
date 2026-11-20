package io.github.gciatto.ktconstraints.js

/** Either a [solution] or, last, the single completion [outcome]. */
@JsExport
class SolutionEvent internal constructor(
    /** The solution or incumbent, or null for the completion event. */
    val solution: Solution?,
    /** The definitive outcome on the completion event, otherwise null. */
    val outcome: Outcome?,
)
