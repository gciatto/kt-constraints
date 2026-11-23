package io.github.gciatto.ktconstraints.solve.store

/** What a [ConstraintStore] established about its constraints after a post. */
enum class StoreStatus {
    /** Proven unsatisfiable. */
    FAILED,

    /** Proven satisfiable by a solver check. */
    SATISFIABLE,

    /** Propagation reached a fixpoint without failure; satisfiability is not proven. */
    CONSISTENT,

    /** Undecided, for example because of a timeout; never a failure. */
    UNKNOWN,
}
