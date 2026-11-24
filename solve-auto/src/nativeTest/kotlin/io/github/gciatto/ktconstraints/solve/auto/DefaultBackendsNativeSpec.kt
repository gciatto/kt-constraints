package io.github.gciatto.ktconstraints.solve.auto

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/** Native bundles the Z3 C API route, loaded at run time. */
class DefaultBackendsNativeSpec :
    FunSpec({
        test("defaults/native-registers-z3") {
            DefaultBackends.load().use {
                it.registry
                    .require("z3/native")
                    .descriptor.id shouldBe "z3/native"
            }
        }
    })
