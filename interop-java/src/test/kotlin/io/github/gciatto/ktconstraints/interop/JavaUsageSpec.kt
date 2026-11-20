package io.github.gciatto.ktconstraints.interop

import io.github.gciatto.ktconstraints.solve.SolverCapability
import io.github.gciatto.ktconstraints.solve.choco.ChocoBackend
import io.github.gciatto.ktconstraints.solve.cpsat.CpSatBackend
import io.github.gciatto.ktconstraints.solve.mip.OrToolsMipBackend
import io.github.gciatto.ktconstraints.solve.spi.SolverBackend
import io.github.gciatto.ktconstraints.solve.z3.Z3Backend
import io.kotest.core.spec.style.FunSpec

/** Runs the javac-compiled [JavaUsage] consumer against every bundled JVM backend. */
class JavaUsageSpec :
    FunSpec({
        val backends: List<Pair<String, () -> SolverBackend>> =
            listOf(
                "choco/direct" to { ChocoBackend() },
                "cp-sat/direct" to { CpSatBackend() },
                "z3/direct" to { Z3Backend() },
                "scip/or-tools" to { OrToolsMipBackend.scip() },
                "highs/or-tools" to { OrToolsMipBackend.highs(60_000) },
            )
        for ((id, backend) in backends) {
            test("java/$id/blocking-satisfaction") { JavaUsage.satisfy(backend()) }
            test("java/$id/async-optimization") { JavaUsage.optimizeAsync(backend()) }
            if (SolverCapability.ENUMERATION in backend().descriptor.capabilities) {
                test("java/$id/iterated-enumeration") { JavaUsage.enumerate(backend()) }
            }
        }
        test("java/numbers") { JavaUsage.numbers() }
        test("java/z3/direct/exact-reals") { JavaUsage.reals(Z3Backend()) }
        test("java/z3/direct/exact-integers") { JavaUsage.exactIntegers(Z3Backend()) }
        test("java/choco/direct/future-cancellation") { JavaUsage.cancelAsync(ChocoBackend()) }
    })
