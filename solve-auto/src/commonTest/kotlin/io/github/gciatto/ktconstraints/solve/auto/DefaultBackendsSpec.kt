package io.github.gciatto.ktconstraints.solve.auto

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/** Every platform registers the pure-Kotlin route. */
class DefaultBackendsSpec :
    FunSpec({
        test("defaults/kotlin-is-registered-on-every-platform") {
            AutoBackend.load().use { auto ->
                auto.registry
                    .require("kotlin/direct")
                    .descriptor.id shouldBe "kotlin/direct"
            }
        }
    })
