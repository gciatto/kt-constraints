package io.github.gciatto.ktconstraints.solve.spi

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/** The native target detects its runtime. */
class PlatformNativeSpec :
    FunSpec({
        test("platform/native-runtime-is-detected") {
            Platform.current().runtime shouldBe Runtime.NATIVE
        }
    })
