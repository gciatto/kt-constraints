package io.github.gciatto.ktconstraints.solve.portfolio.examples.problems

import io.github.gciatto.ktconstraints.dsl.allDifferent
import io.github.gciatto.ktconstraints.dsl.neq
import io.github.gciatto.ktconstraints.model.ConstraintScope
import io.github.gciatto.ktconstraints.solve.SolverCapability
import io.github.gciatto.ktconstraints.solve.incremental.CheckStatus
import io.github.gciatto.ktconstraints.solve.incremental.IncrementalConstraintSolver
import io.github.gciatto.ktconstraints.solve.portfolio.DefaultBackends
import io.github.gciatto.ktconstraints.solve.portfolio.solveAuto
import io.github.gciatto.ktconstraints.solve.result.SolveStatus
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

internal suspend fun pigeonhole() {
    // --8<-- [start:model]
    val scope = ConstraintScope.empty()
    val holes = List(3) { scope.intVar("hole-for-pigeon-$it", 0..1) }
    scope.add(allDifferent(*holes.toTypedArray()))
    val model = scope.build()
    // --8<-- [end:model]

    // --8<-- [start:solve]
    val result = solveAuto(model)
    println(result.status)
    // --8<-- [end:solve]
    result.status shouldBe SolveStatus.INFEASIBLE
    result.assignment shouldBe null
}

internal suspend fun pigeonholeCore() {
    // --8<-- [start:session]
    val scope = ConstraintScope.empty()
    val holes = List(3) { scope.intVar("hole-for-pigeon-$it", 0..1) }
    val separate =
        listOf(
            holes[0] neq holes[1],
            holes[0] neq holes[2],
            holes[1] neq holes[2],
        )
    val model = scope.build()
    // Sessions need a solver with incremental assertions: pick one among the platform's defaults.
    DefaultBackends.load().use { defaults ->
        val backend = defaults.registry.select(model, requiredCapabilities = setOf(SolverCapability.INCREMENTAL_ASSERTIONS))
        backend.create().use { solver ->
            (solver as IncrementalConstraintSolver).openSession(model).use { session ->
                val conflict = session.check(assumptions = separate)
                println(conflict.status)
                val core = requireNotNull(conflict.unsatCore)
                println("Conflicting pairwise requirements: ${core.size}")
                conflict.status shouldBe CheckStatus.UNSATISFIABLE
                separate.containsAll(core) shouldBe true
                core.size shouldBe 3
                session.check(core).status shouldBe CheckStatus.UNSATISFIABLE
                for (omitted in separate.indices) {
                    session.check(separate.filterIndexed { index, _ -> index != omitted }).status shouldBe
                        CheckStatus.SATISFIABLE
                }
                // Assumptions apply to one check; the root model is still satisfiable.
                val recovered = session.check()
                println(recovered.status)
                recovered.status shouldBe CheckStatus.SATISFIABLE
            }
        }
    }
    // --8<-- [end:session]
}

/** Executable Pigeonhole cookbook example. */
class PigeonholeSpec :
    FunSpec({
        test("prove three pigeons cannot occupy two distinct holes") { pigeonhole() }
        test("explain infeasibility through temporary pairwise assumptions") { pigeonholeCore() }
    })
