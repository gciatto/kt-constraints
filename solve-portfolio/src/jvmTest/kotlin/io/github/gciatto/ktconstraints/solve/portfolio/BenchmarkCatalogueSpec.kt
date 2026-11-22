package io.github.gciatto.ktconstraints.solve.portfolio

import io.github.gciatto.ktconstraints.solve.cpsat.CpSatBackend
import io.github.gciatto.ktconstraints.solve.spi.SolverBackend
import io.github.gciatto.ktconstraints.solve.z3.Z3Backend
import io.github.gciatto.ktconstraints.test.solve.BenchmarkCatalogue
import io.github.gciatto.ktconstraints.test.solve.BenchmarkInstance
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withTests
import io.kotest.matchers.shouldBe

/** Two independent engines confirm each benchmark's known answer, so benchmark correctness checks are trustworthy. */
class BenchmarkCatalogueSpec :
    FunSpec({
        val reseeded = BenchmarkCatalogue.instances(seed = 1).filter { it.family in BenchmarkCatalogue.SEEDED }
        withTests(
            nameFn = { "benchmark-catalogue/known-answer/$it" },
            BenchmarkCatalogue.instances() +
                reseeded.map { BenchmarkInstance("${it.family}-seed1", it.size, it.model, it.expectedStatus, it.expectedObjective) },
        ) { instance ->
            val routes: List<SolverBackend> = listOf(CpSatBackend(), Z3Backend()).filter { it.support(instance.model).isSupported }
            routes.isNotEmpty() shouldBe true
            for (route in routes) {
                route.create().use { solver ->
                    val result = solver.solve(instance.model)
                    result.status shouldBe instance.expectedStatus
                    result.objectiveValue shouldBe instance.expectedObjective
                }
            }
        }
    })
