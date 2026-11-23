package io.github.gciatto.ktconstraints.solve.spi

import io.github.gciatto.ktconstraints.solve.spi.support.SupportReport
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/** Support diagnostics are immutable and distinguish lossless lowering from rejection. */
class SupportReportSpec :
    FunSpec({
        test("support/rejection-is-distinct-from-lossless-lowering") {
            val unsupported = mutableListOf("Integer out of range")
            val lowerings = mutableListOf("allDifferent lowered losslessly")
            val report = SupportReport(unsupported, lowerings)
            unsupported.clear()
            lowerings.clear()
            runCatching { (report.unsupported as MutableList).clear() }
            report.isSupported shouldBe false
            report.unsupported shouldBe listOf("Integer out of range")
            report.losslessDecompositions shouldBe listOf("allDifferent lowered losslessly")
            SupportReport(losslessDecompositions = listOf("exact linear lowering")).isSupported shouldBe true
        }

        test("availability/unavailable-runtime-must-explain-why") {
            shouldThrow<IllegalArgumentException> { BackendAvailability(false) }
            shouldThrow<IllegalArgumentException> { BackendAvailability(false, " ") }
            BackendAvailability(true).available shouldBe true
        }
    })
