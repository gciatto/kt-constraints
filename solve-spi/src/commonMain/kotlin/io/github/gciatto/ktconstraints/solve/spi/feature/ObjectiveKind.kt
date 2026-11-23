package io.github.gciatto.ktconstraints.solve.spi.feature

/** Shape of a model's objective. */
enum class ObjectiveKind {
    /** Satisfaction. */
    NONE,

    /** A linear integer expression. */
    LINEAR,

    /** An integer expression with products of variables or other non-linear nodes. */
    NONLINEAR,
}
