package io.github.gciatto.ktconstraints.solve

/** Outcome of one incremental satisfiability check; termination is reported separately. */
enum class CheckStatus {
    /** The active assertions and assumptions have a validated model. */
    SATISFIABLE,

    /** The engine proved the active assertions and assumptions jointly unsatisfiable. */
    UNSATISFIABLE,

    /** No proof either way; never evidence of unsatisfiability. */
    UNKNOWN,
}
