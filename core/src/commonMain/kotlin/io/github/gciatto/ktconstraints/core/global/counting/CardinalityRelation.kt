package io.github.gciatto.ktconstraints.core.global.counting

/** Comparison between the number of true operands and a cardinality bound. */
enum class CardinalityRelation {
    /** At most the bound. */
    AT_MOST,

    /** Exactly the bound. */
    EXACTLY,

    /** At least the bound. */
    AT_LEAST,
}
