package io.github.gciatto.ktconstraints.js

/** Answer of [Store.entails]. */
@JsExport
class EntailmentAnswer internal constructor(
    /** `ENTAILED`, `DISENTAILED`, `UNDETERMINED` (proven neither) or `UNKNOWN` (the engine could not tell). */
    val value: String,
    /** `SATISFIABILITY` (definite answers are proofs) or `PROPAGATION`. */
    val evidence: String,
)
