package io.github.gciatto.ktconstraints.solve.spi

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/** The js target detects its runtime. */
class PlatformJsSpec :
    FunSpec({
        test("platform/js-runtime-is-detected") {
            Platform.current().runtime shouldBe Runtime.NODE
        }
    })
