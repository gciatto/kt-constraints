package io.github.gciatto.ktconstraints.core

/** Mathematical sort of a symbolic expression; never a backend-specific native type. */
sealed interface Sort {
    /** Classical two-valued Boolean sort. */
    data object Bool : Sort

    /** Mathematical integer sort; backend encodings may impose additional bounds. */
    data object Integer : Sort

    /** Mathematical real sort (exactness is determined by the actual expression and backend). */
    data object Real : Sort

    /** Fixed-width bit-vector sort (distinct from [Integer]). */
    data class BitVector(
        val width: Int,
    ) : Sort {
        init {
            require(width > 0) { "Bit-vector width must be positive" }
        }
    }
}
