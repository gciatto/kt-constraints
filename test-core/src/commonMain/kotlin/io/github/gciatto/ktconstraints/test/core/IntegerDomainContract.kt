package io.github.gciatto.ktconstraints.test.core

import io.github.gciatto.ktconstraints.core.domain.IntDomain
import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.funSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import org.gciatto.kt.math.BigInteger

/** Reusable finite-domain contract checked against independently enumerated small integer sets. */
@OptIn(ExperimentalKotest::class)
fun integerDomainContract(create: (Long, Long) -> IntDomain) =
    funSpec {
        test("domain-contract/union-intersection-and-cardinality-match-sets") {
            checkAll(PropTestConfig(iterations = 100, seed = 104729L), Arb.int(-10..10), Arb.int(-10..10)) { a, b ->
                val first = create(minOf(a, b).toLong(), maxOf(a, b).toLong())
                val second = create(-3, 3)
                val firstSet = (minOf(a, b)..maxOf(a, b)).toSet()
                val secondSet = (-3..3).toSet()
                val union = first.union(second)
                val intersection = first.intersect(second)
                union.cardinality shouldBe BigInteger.of((firstSet + secondSet).size)
                intersection.cardinality shouldBe BigInteger.of(firstSet.intersect(secondSet).size)
                for (value in -12..12) {
                    (value.toLong() in union) shouldBe (value in firstSet || value in secondSet)
                    (value.toLong() in intersection) shouldBe (value in firstSet && value in secondSet)
                }
                first.union(second) shouldBe second.union(first)
                first.intersect(second) shouldBe second.intersect(first)
                first.union(first) shouldBe first
                first.intersect(first) shouldBe first
            }
        }
    }
