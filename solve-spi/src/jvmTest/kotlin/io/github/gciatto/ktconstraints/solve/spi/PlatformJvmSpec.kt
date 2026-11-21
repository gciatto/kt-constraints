package io.github.gciatto.ktconstraints.solve.spi

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/** The jvm target detects its runtime. */
class PlatformJvmSpec :
    FunSpec({
        test("platform/jvm-runtime-is-detected") {
            Platform.current().runtime shouldBe Runtime.JVM
        }
    })
