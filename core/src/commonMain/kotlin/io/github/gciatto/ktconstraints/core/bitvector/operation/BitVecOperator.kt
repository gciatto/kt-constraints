package io.github.gciatto.ktconstraints.core.bitvector.operation

/** Binary bit-vector operations; results are taken modulo 2^width as in SMT-LIB. */
enum class BitVecOperator {
    /** Wrapping addition (`bvadd`). */
    ADD,

    /** Wrapping subtraction (`bvsub`). */
    SUBTRACT,

    /** Wrapping multiplication (`bvmul`). */
    MULTIPLY,

    /** Bitwise conjunction (`bvand`). */
    AND,

    /** Bitwise disjunction (`bvor`). */
    OR,

    /** Bitwise exclusive disjunction (`bvxor`). */
    XOR,

    /** Left shift by an unsigned amount; zero once the amount reaches the width (`bvshl`). */
    SHIFT_LEFT,

    /** Zero-filling right shift; zero once the amount reaches the width (`bvlshr`). */
    LOGICAL_SHIFT_RIGHT,

    /** Sign-filling right shift; all sign bits once the amount reaches the width (`bvashr`). */
    ARITHMETIC_SHIFT_RIGHT,
}
