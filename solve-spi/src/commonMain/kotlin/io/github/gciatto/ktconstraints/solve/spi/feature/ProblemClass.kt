package io.github.gciatto.ktconstraints.solve.spi.feature

/** Coarse problem family of a model; ranking heuristics and benchmark evidence are keyed by it. */
enum class ProblemClass {
    /** Booleans only. */
    PURE_BOOLEAN,

    /** Linear integer arithmetic with top-level relations only. */
    LINEAR_INTEGER,

    /** A linear integer program whose integer variables all range over `0..1`: a pseudo-Boolean program. */
    BINARY_PROGRAM,

    /** Linear relations over reals only, with a linear objective if any: a linear program. */
    LINEAR_PROGRAM,

    /** Linear relations over reals and integers, with a linear objective if any: a mixed-integer linear program. */
    MIXED_INTEGER_PROGRAM,

    /** Integer arithmetic with products of variables. */
    NONLINEAR_INTEGER,

    /** Global constraints or reified relations over integers. */
    CP_GLOBALS,

    /** Exact real arithmetic mixed with Boolean logic or other non-linear-program structure. */
    SMT_REAL,

    /** Fixed-width bit-vectors. */
    SMT_BITVECTOR,
}
