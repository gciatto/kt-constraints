package io.github.gciatto.ktconstraints.js.incremental
import io.github.gciatto.ktconstraints.solve.incremental.Checkpoint as SessionCheckpoint

/** Opaque restoration point of one [Session]; stale once it or an older checkpoint is restored. */
@JsExport
class Checkpoint internal constructor(
    internal val checkpoint: SessionCheckpoint,
)
