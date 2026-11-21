package io.github.gciatto.ktconstraints.solve.spi

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
}
