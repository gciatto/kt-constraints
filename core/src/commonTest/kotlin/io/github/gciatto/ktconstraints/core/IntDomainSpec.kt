package io.github.gciatto.ktconstraints.core

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.gciatto.kt.math.BigInteger

/** Shared integer domain behavior, executed by Kotest on supported targets. */
class IntDomainSpec :
    FunSpec({
        test("of/closed-interval-membership") {
            val domain = IntDomain.of(1, 10)
            (1L in domain) shouldBe true
            (10L in domain) shouldBe true
            (11L in domain) shouldBe false
        }

        test("of/reject-inverted-interval") {
            shouldThrow<IllegalArgumentException> { IntDomain.of(10, 1) }
        }

        test("empty/has-no-bounds-and-zero-cardinality") {
            val domain = IntDomain.empty()
            domain.isEmpty shouldBe true
            domain.isFinite shouldBe true
            domain.min shouldBe null
            domain.max shouldBe null
            domain.cardinality shouldBe BigInteger.ZERO
            (0L in domain) shouldBe false
            IntDomain.of(IntRange.EMPTY) shouldBe domain
        }

        test("sparse/sorts-deduplicates-and-preserves-holes") {
            val domain = IntDomain.values(4, 1, 4, 2, 9)
            domain.min shouldBe BigInteger.ONE
            domain.max shouldBe BigInteger.of(9)
            domain.cardinality shouldBe BigInteger.of(4)
            (3L in domain) shouldBe false
            (4L in domain) shouldBe true
            domain shouldBe IntDomain.values(9, 2, 1, 4)
            domain.hashCode() shouldBe IntDomain.values(9, 2, 1, 4).hashCode()
            IntDomain.values() shouldBe IntDomain.empty()
        }

        test("union/normalizes-overlap-and-adjacency") {
            val domain = IntDomain.of(4, 8).union(IntDomain.of(1, 4)).union(IntDomain.of(9, 12))
            domain shouldBe IntDomain.of(1, 12)
            domain.intervals.size shouldBe 1
        }

        test("intersection/preserves-disconnected-pieces") {
            val left = IntDomain.of(1, 5).union(IntDomain.of(8, 12))
            val right = IntDomain.of(3, 9)
            left.intersect(right) shouldBe IntDomain.of(3, 5).union(IntDomain.of(8, 9))
            left.intersect(IntDomain.of(20, 30)) shouldBe IntDomain.empty()
            left.intersect(IntDomain.empty()) shouldBe IntDomain.empty()
            left.union(IntDomain.empty()) shouldBe left
        }

        test("interval/exact-bounds-and-cardinality-without-enumeration") {
            val beyondLong = BigInteger.of("9223372036854775808")
            val domain = IntDomain.of(BigInteger.of(Long.MIN_VALUE), beyondLong)
            domain.cardinality shouldBe BigInteger.of("18446744073709551617")
            (beyondLong in domain) shouldBe true
            (beyondLong + BigInteger.ONE in domain) shouldBe false
        }

        test("union/adjacency-does-not-overflow-at-long-boundaries") {
            val last = BigInteger.of(Long.MAX_VALUE)
            IntDomain.of(last, last).union(IntDomain.of(last + 1, last + 2)) shouldBe IntDomain.of(last, last + 2)
        }

        test("intervals/input-and-output-collections-cannot-mutate-domain") {
            val input = mutableListOf(IntInterval(BigInteger.ONE, BigInteger.TWO))
            val domain = IntDomain.ofIntervals(input)
            input.clear()
            runCatching { (domain.intervals as MutableList).clear() }
            domain shouldBe IntDomain.of(1, 2)
        }
    })
