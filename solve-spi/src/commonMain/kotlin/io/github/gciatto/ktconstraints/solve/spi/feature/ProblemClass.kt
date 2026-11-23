package io.github.gciatto.ktconstraints.solve.spi.feature

/** Coarse problem family of a model; ranking heuristics and benchmark evidence are keyed by it. */
enum class ProblemClass {
    /** Booleans only. */
    PURE_BOOLEAN,

    /** Linear integer arithmetic with top-level relations only. */
    LINEAR_INTEGER,

    /** Integer arithmetic with products of variables. */
    NONLINEAR_INTEGER,

    /** Global constraints or reified relations over integers. */
    CP_GLOBALS,

    /** Exact real arithmetic, possibly mixed with integers. */
    SMT_REAL,

    /** Fixed-width bit-vectors. */
    SMT_BITVECTOR,
}
