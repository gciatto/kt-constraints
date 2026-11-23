package io.github.gciatto.ktconstraints.core.bitvector.operation

/** Bit-vector comparisons; signed relations read operands in two's complement. */
enum class BitVecRelation {
    /** Equality. */
    EQUAL,

    /** Unsigned strict order (`bvult`). */
    UNSIGNED_LESS_THAN,

    /** Unsigned non-strict order (`bvule`). */
    UNSIGNED_LESS_OR_EQUAL,

    /** Signed strict order (`bvslt`). */
    SIGNED_LESS_THAN,

    /** Signed non-strict order (`bvsle`). */
    SIGNED_LESS_OR_EQUAL,
}
