package io.github.gciatto.ktconstraints.core

import kotlin.jvm.JvmStatic

/** Finite range of mathematically integer-valued elements, initially bounded by [Long]. */
interface IntDomain : Domain {
    /** Inclusive lower bound. */
    val min: Long

    /** Inclusive upper bound. */
    val max: Long

    override val isFinite: Boolean get() = true

    /** Checks membership without enumerating the range. */
    operator fun contains(value: Long): Boolean = value in min..max

    /** Factories following the 2p-Kt companion-of convention. */
    companion object {
        /** Creates an inclusive integer interval. */
        @JvmStatic
        fun of(
            min: Long,
            max: Long,
        ): IntDomain {
            require(min <= max) { "Expected min <= max, got $min > $max" }
            return BoundedIntDomain(min, max)
        }

        /** Creates an inclusive integer interval. */
        @JvmStatic
        fun of(range: IntRange): IntDomain = of(range.first.toLong(), range.last.toLong())
    }
}

private data class BoundedIntDomain(
    override val min: Long,
    override val max: Long,
) : IntDomain
