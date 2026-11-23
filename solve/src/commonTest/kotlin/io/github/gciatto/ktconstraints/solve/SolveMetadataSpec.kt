package io.github.gciatto.ktconstraints.solve

import io.github.gciatto.ktconstraints.solve.result.SolveMetadata
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/** Diagnostic metadata preserves raw numeric lexemes and caller isolation. */
class SolveMetadataSpec :
    FunSpec({
        test("metadata/is-detached-and-immutable") {
            val messages = mutableListOf("runtime warning")
            val statistics = mutableMapOf("solveTime" to "0.000125")
            val metadata = SolveMetadata(messages, statistics)
            messages.clear()
            statistics.clear()
            runCatching { (metadata.messages as MutableList).clear() }
            runCatching { (metadata.statistics as MutableMap).clear() }
            metadata.messages shouldBe listOf("runtime warning")
            metadata.statistics shouldBe mapOf("solveTime" to "0.000125")
        }
    })
