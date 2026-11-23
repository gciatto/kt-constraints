package io.github.gciatto.ktconstraints.solve.store

/** Answer to [ConstraintStore.entails]. */
enum class Entailment {
    /** Every solution of the store satisfies the constraint. */
    ENTAILED,

    /** No solution of the store satisfies the constraint. */
    DISENTAILED,

    /** Proven neither entailed nor disentailed: some solutions satisfy it and some do not. */
    UNDETERMINED,

    /** The engine could not tell, for example after a timeout or with propagation too weak to decide. */
    UNKNOWN,
}
