package io.github.gciatto.ktconstraints.solve.minizinc

import io.github.gciatto.ktconstraints.solve.spi.BackendUnavailableException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withTests
import io.kotest.matchers.shouldBe

/** Each requested installed configuration must pass the same independent contracts. */
class MiniZincCatalogueSpec : FunSpec({
    withTests(nameFn = { "catalogue/missing-executable/$it" }, MiniZincEngine.entries) { engine ->
        val backend = MiniZincBackend(engine, "/nonexistent/kt-constraints/minizinc")
        backend.availability().available shouldBe false
        shouldThrow<BackendUnavailableException> { backend.create() }
    }
})
