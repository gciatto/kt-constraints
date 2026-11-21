package io.github.gciatto.ktconstraints.js

import io.github.gciatto.ktconstraints.solve.StoreMark

/** Opaque backtracking point of one [Store]; stale once undone past. */
@JsExport
class Mark internal constructor(
    internal val mark: StoreMark,
)
