package io.github.gciatto.ktconstraints.solve

/** How a [ConstraintStore] obtained an answer. */
enum class Evidence {
    /** Complete satisfiability checks, so definite answers are proofs. */
    SATISFIABILITY,

    /** Native propagation: definite answers are sound, but weak propagation answers [Entailment.UNKNOWN]. */
    PROPAGATION,
}
