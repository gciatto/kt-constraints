package io.github.gciatto.ktconstraints.solve.spi.feature

/** Global constraints a model may use. */
enum class GlobalKind {
    /** `allDifferent`. */
    ALL_DIFFERENT,

    /** `table`. */
    TABLE,

    /** `element`. */
    ELEMENT,

    /** Boolean cardinality (`atMost`, `atLeast`, `exactly`). */
    CARDINALITY,

    /** Integer `min` and `max`. */
    MIN_MAX,

    /** `inverse`. */
    INVERSE,

    /** `circuit`. */
    CIRCUIT,

    /** `cumulative` (and `disjunctive`). */
    CUMULATIVE,

    /** `regular` (deterministic automata). */
    REGULAR,

    /** `subCircuit`. */
    SUBCIRCUIT,

    /** `binPacking`. */
    BIN_PACKING,

    /** `noOverlap2D`. */
    NO_OVERLAP_2D,
}
