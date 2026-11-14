package io.github.gciatto.ktconstraints.core

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

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
    })
