package io.github.gciatto.ktconstraints.solve

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/** Typed per-backend tuning travels with the solve options; each route reads only its own. */
class SolveOptionsSpec :
    FunSpec({
        data class Alpha(
            val level: Int,
        ) : BackendOptions

        data class Beta(
            val flag: Boolean,
        ) : BackendOptions

        test("options/backend-options-are-typed-and-replace-same-type-entries") {
            val options = SolveOptions(timeoutMillis = 10) + Alpha(1) + Beta(true) + Alpha(2)
            options.backend<Alpha>() shouldBe Alpha(2)
            options.backend<Beta>() shouldBe Beta(true)
            options.backendOptions shouldBe listOf(Beta(true), Alpha(2))
            options.timeoutMillis shouldBe 10L
            SolveOptions.DEFAULT.backend<Alpha>() shouldBe null
        }
    })
