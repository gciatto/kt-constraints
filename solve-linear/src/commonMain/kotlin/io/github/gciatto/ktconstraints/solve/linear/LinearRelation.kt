package io.github.gciatto.ktconstraints.solve.linear

/** How a [LinearRow]'s left-hand side compares with its right-hand side. */
enum class LinearRelation {
    /** `Σ c·x ≤ rhs`. */
    LESS_OR_EQUAL,

    /** `Σ c·x < rhs`; only over reals, since integer strictness becomes `≤ rhs − 1` exactly. */
    LESS,

    /** `Σ c·x = rhs`. */
    EQUAL,
}
