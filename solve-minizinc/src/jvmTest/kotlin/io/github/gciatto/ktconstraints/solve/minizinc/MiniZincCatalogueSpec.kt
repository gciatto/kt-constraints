package io.github.gciatto.ktconstraints.solve.minizinc

import io.github.gciatto.ktconstraints.core.IntDomain
import io.github.gciatto.ktconstraints.model.ConstraintScope
import io.github.gciatto.ktconstraints.solve.SolveStatus
import io.github.gciatto.ktconstraints.solve.spi.BackendUnavailableException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withTests
import io.kotest.matchers.shouldBe
import java.nio.file.Files

/** Each requested installed configuration must pass the same independent contracts. */
class MiniZincCatalogueSpec :
    FunSpec({
        withTests(nameFn = { "catalogue/missing-executable/$it" }, MiniZincEngine.entries) { engine ->
            val backend = MiniZincBackend(engine, "/nonexistent/kt-constraints/minizinc")
            backend.availability().available shouldBe false
            shouldThrow<BackendUnavailableException> { backend.create() }
        }
        test("catalogue/configuration-presence-does-not-prove-runtime-availability")
            .config(enabled = !System.getProperty("os.name").startsWith("Windows")) {
                val directory = Files.createTempDirectory("minizinc broken configuration ")
                try {
                    val script = directory.resolve("minizinc")
                    Files.writeString(
                        script,
                        "#!/bin/sh\nif [ \"${'$'}1\" = \"--solvers-json\" ]; then\n" +
                            "echo '[{\"id\":\"org.chuffed.chuffed\"}]'\nelse\nexit 3\nfi\n",
                    )
                    script.toFile().setExecutable(true) shouldBe true
                    val backend = MiniZincBackend(MiniZincEngine.CHUFFED, script.toString())
                    backend.availability().available shouldBe false
                    shouldThrow<BackendUnavailableException> { backend.create() }
                    MiniZincBackend(MiniZincEngine.GECODE, script.toString()).availability().available shouldBe false
                } finally {
                    directory.toFile().deleteRecursively() shouldBe true
                }
            }
        val executable = System.getenv("MINIZINC_EXECUTABLE")
        val requested = System.getenv("MINIZINC_ENGINES")?.split(',') ?: listOf("gecode")
        for (engine in MiniZincEngine.entries) {
            test("catalogue/exact-integer-boundaries/${engine.engineId}")
                .config(enabled = executable != null && engine.engineId in requested) {
                    for (value in listOf(-engine.maximumInteger, engine.maximumInteger)) {
                        val scope = ConstraintScope.empty()
                        val x = scope.intVar("x", IntDomain.of(value, value))
                        scope.maximize(x)
                        MiniZincBackend(engine, requireNotNull(executable)).create().use { solver ->
                            val result = solver.solve(scope.build())
                            result.descriptor shouldBe engine.descriptor
                            result.status shouldBe SolveStatus.OPTIMAL
                            result.objectiveValue shouldBe value
                            result.assignment?.valueOf(x) shouldBe value
                        }
                    }
                }
        }
    })
