package io.github.gciatto.ktconstraints.solve

/** An [Entailment] with the [Evidence] behind it. */
data class EntailmentResult(
    /** The answer. */
    val value: Entailment,
    /** How it was obtained. */
    val evidence: Evidence,
)
