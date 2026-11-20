package io.github.gciatto.ktconstraints.js

import io.github.gciatto.ktconstraints.model.ConstraintModel

/** Immutable model, solvable any number of times. */
@JsExport
class Model internal constructor(
    internal val model: ConstraintModel,
)
