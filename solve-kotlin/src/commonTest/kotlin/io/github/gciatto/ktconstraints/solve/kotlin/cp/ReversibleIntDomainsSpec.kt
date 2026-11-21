package io.github.gciatto.ktconstraints.solve.kotlin.cp

import io.github.gciatto.ktconstraints.core.IntDomain
import io.github.gciatto.ktconstraints.model.ConstraintScope
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.gciatto.kt.math.BigInteger

/** Exact domain restriction and rollback contracts, independent of solver search. */
class ReversibleIntDomainsSpec :
    FunSpec({
        test("domains/restriction-keeps-holes-and-detaches-snapshots") {
            val scope = ConstraintScope.empty()
            val x = scope.intVar("x", IntDomain.values(1, 4, 7, 10))
            val store = ReversibleIntDomains(listOf(x))
            val snapshot = store.snapshot()

            store.narrow(x, IntDomain.of(3, 8), 2) shouldBe true
            store[x] shouldBe IntDomain.values(4, 7)
            snapshot[x] shouldBe x.domain
            x.domain shouldBe IntDomain.values(1, 4, 7, 10)
            store.reductions.single().sourceAssertion shouldBe 2
            store.reductions.single().before shouldBe x.domain
            store.reductions.single().after shouldBe IntDomain.values(4, 7)
            store.narrow(x, IntDomain.of(0, 100), 3) shouldBe false
            store.reductions.size shouldBe 1
        }

        test("domains/large-endpoints-are-exact-and-never-enumerated") {
            val scope = ConstraintScope.empty()
            val limit = BigInteger.TEN.pow(100)
            val x = scope.intVar("x", IntDomain.of(-limit, limit))
            val store = ReversibleIntDomains(listOf(x))

            store.narrow(x, IntDomain.of(limit - 1, limit), 0) shouldBe true
            store[x].min shouldBe limit - 1
            store[x].max shouldBe limit
            store[x].cardinality shouldBe BigInteger.TWO
        }

        test("domains/nested-rollback-invalidates-restored-and-younger-checkpoints") {
            val scope = ConstraintScope.empty()
            val x = scope.intVar("x", 0..10)
            val store = ReversibleIntDomains(listOf(x))
            val outer = store.push()
            store.narrow(x, IntDomain.of(2, 8), 0)
            val inner = store.push()
            store.narrow(x, IntDomain.of(4, 6), 1)

            store.popTo(outer)
            store[x] shouldBe x.domain
            store.reductions.size shouldBe 0
            shouldThrow<IllegalArgumentException> { store.popTo(inner) }
            shouldThrow<IllegalArgumentException> { store.popTo(outer) }

            val sibling = store.push()
            store.narrow(x, IntDomain.of(9, 10), 2)
            store.popTo(sibling)
            store[x] shouldBe x.domain
        }

        test("domains/foreign-checkpoints-and-variables-do-not-change-the-store") {
            val scope = ConstraintScope.empty()
            val x = scope.intVar("x", 0..3)
            val store = ReversibleIntDomains(listOf(x))
            val other = ReversibleIntDomains(listOf(x))
            val foreign = other.push()
            val alien = ConstraintScope.empty().intVar("x", 0..3)

            shouldThrow<IllegalArgumentException> { store.popTo(foreign) }
            shouldThrow<IllegalArgumentException> { store.narrow(alien, IntDomain.empty(), 0) }
            store[x] shouldBe x.domain
            store.reductions.size shouldBe 0
        }

        test("domains/empty-restrictions-are-restorable") {
            val scope = ConstraintScope.empty()
            val x = scope.intVar("x", 0..3)
            val store = ReversibleIntDomains(listOf(x))
            val mark = store.push()
            store.narrow(x, IntDomain.of(7, 8), 0) shouldBe true
            store[x].isEmpty shouldBe true
            store.popTo(mark)
            store[x] shouldBe x.domain
        }
    })
