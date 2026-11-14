package io.github.gciatto.ktconstraints.core

import io.kotest.core.spec.style.FunSpec

/** Runs a separately compiled Java consumer of the core API. */
class JavaUsageSpec :
    FunSpec({
        test("java/exact-integer-consumer") { JavaUsage.verify() }
    })
