package io.github.gciatto.ktconstraints.core

import kotlin.jvm.JvmStatic
import org.gciatto.kt.math.BigInteger

/** Finite set of exact integers, stored without enumerating its elements. */
interface IntDomain : Domain {
    /** Immutable sorted, disjoint and non-adjacent closed intervals. */
    val intervals: List<IntInterval>

    /** Inclusive lower bound, or null for the empty domain. */
    val min: BigInteger? get() = intervals.firstOrNull()?.min

    /** Inclusive upper bound, or null for the empty domain. */
    val max: BigInteger? get() = intervals.lastOrNull()?.max

    /** Whether this domain contains no values. */
    val isEmpty: Boolean get() = intervals.isEmpty()

    /** Exact number of values, even when greater than [Long.MAX_VALUE]. */
    val cardinality: BigInteger
        get() = intervals.fold(BigInteger.ZERO) { total, interval -> total + interval.max - interval.min + 1 }

    override val isFinite: Boolean get() = true

    /** Checks membership without enumerating the range. */
    operator fun contains(value: BigInteger): Boolean = intervals.any { value >= it.min && value <= it.max }

    /** Checks machine integer membership without narrowing. */
    operator fun contains(value: Long): Boolean = contains(BigInteger.of(value))

    /** Returns the canonical set union. */
    fun union(other: IntDomain): IntDomain = ofIntervals(intervals + other.intervals)

    /** Returns the exact set intersection without enumerating values. */
    fun intersect(other: IntDomain): IntDomain {
        val result = mutableListOf<IntInterval>()
        var leftIndex = 0
        var rightIndex = 0
        while (leftIndex < intervals.size && rightIndex < other.intervals.size) {
            val left = intervals[leftIndex]
            val right = other.intervals[rightIndex]
            val lower = maxOf(left.min, right.min)
            val upper = minOf(left.max, right.max)
            if (lower <= upper) result += IntInterval(lower, upper)
            if (left.max < right.max) leftIndex++ else rightIndex++
        }
        return ofIntervals(result)
    }

    /** Factories following the 2p-Kt companion-of convention. */
    companion object {
        /** Creates an inclusive integer interval. */
        @JvmStatic
        fun of(
            min: Long,
            max: Long,
        ): IntDomain = of(BigInteger.of(min), BigInteger.of(max))

        /** Creates an inclusive interval with arbitrary-precision bounds. */
        @JvmStatic
        fun of(
            min: BigInteger,
            max: BigInteger,
        ): IntDomain = ofIntervals(listOf(IntInterval(min, max)))

        /** Creates an inclusive integer interval. */
        @JvmStatic
        fun of(range: IntRange): IntDomain = if (range.isEmpty()) empty() else of(range.first.toLong(), range.last.toLong())

        /** Creates an empty finite domain. */
        @JvmStatic
        fun empty(): IntDomain = ofIntervals(emptyList())

        /** Creates a sparse domain, sorting and deduplicating its values. */
        @JvmStatic
        fun values(vararg values: Long): IntDomain = ofIntervals(values.map { IntInterval(BigInteger.of(it), BigInteger.of(it)) })

        /** Copies and canonicalizes intervals, merging overlap and adjacency. */
        @JvmStatic
        fun ofIntervals(intervals: Iterable<IntInterval>): IntDomain {
            val sorted = intervals.sortedBy { it.min }
            val canonical =
                buildList<IntInterval> {
                    for (interval in sorted) {
                        val previous = lastOrNull()
                        if (previous != null && interval.min <= previous.max + 1) {
                            this[lastIndex] = IntInterval(previous.min, maxOf(previous.max, interval.max))
                        } else {
                            add(interval)
                        }
                    }
                }
            return FiniteIntDomain(canonical)
        }
    }
}

private data class FiniteIntDomain(
    override val intervals: List<IntInterval>,
) : IntDomain {
    override fun toString(): String = intervals.joinToString(prefix = "{", postfix = "}")
}
