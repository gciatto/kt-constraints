package io.github.gciatto.ktconstraints.solve.minizinc

import io.github.gciatto.ktconstraints.solve.spi.exception.BackendUnavailableException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/** Missing executables produce diagnostics rather than a fake solver. */
class MiniZincRuntimeSpec :
    FunSpec({
        test("runtime/missing-executable-is-unavailable") {
            val backend = MiniZincGecodeBackend("/nonexistent/kt-constraints/minizinc")
            backend.availability().available shouldBe false
            backend.availability().reason.isNullOrBlank() shouldBe false
            shouldThrow<BackendUnavailableException> { backend.createSolver() }
        }
    })
