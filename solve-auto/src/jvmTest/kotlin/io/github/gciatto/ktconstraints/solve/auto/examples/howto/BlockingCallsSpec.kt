package io.github.gciatto.ktconstraints.solve.auto.examples.howto

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder

/** Runs the blocking Kotlin example on the bundled JVM routes. */
class BlockingCallsSpec :
    FunSpec({
        test("iterate solutions without suspending") {
            blockingSolutions().shouldContainExactlyInAnyOrder(2, 3)
        }
    })
