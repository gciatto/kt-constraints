package io.github.gciatto.ktconstraints.js

import io.github.gciatto.ktconstraints.solve.Checkpoint as SessionCheckpoint

/** Opaque restoration point of one [Session]; stale once it or an older checkpoint is restored. */
@JsExport
class Checkpoint internal constructor(
    internal val checkpoint: SessionCheckpoint,
)
