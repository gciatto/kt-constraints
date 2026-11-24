package io.github.gciatto.ktconstraints.solve.auto.examples.problems

// --8<-- [start:imports]
import io.github.gciatto.ktconstraints.core.integer.IntVar
import io.github.gciatto.ktconstraints.dsl.allDifferent
import io.github.gciatto.ktconstraints.dsl.neq
import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.model.ConstraintScope
import io.github.gciatto.ktconstraints.solve.SolverCapability
import io.github.gciatto.ktconstraints.solve.auto.AutoBackend
import io.github.gciatto.ktconstraints.solve.incremental.IncrementalConstraintSolver
import io.github.gciatto.ktconstraints.solve.result.SolveResult
// --8<-- [end:imports]

// --8<-- [start:model]
internal fun ConstraintScope.pigeonhole(): List<IntVar> {
    val holes = List(3) { intVar("hole-for-pigeon-$it", 0..1) }
    add(allDifferent(*holes.toTypedArray()))
    return holes
}
// --8<-- [end:model]

// --8<-- [start:solve]
internal suspend fun solvePigeonhole(): SolveResult {
    val model = ConstraintModel.of { pigeonhole() }
    val result = AutoBackend.load().use { auto -> auto.createSolver().use { it.solve(model) } }
    println(result.status)
    return result
}
// --8<-- [end:solve]

// --8<-- [start:session]
internal suspend fun pigeonholeCore() =
    ConstraintScope.empty {
        val holes = List(3) { intVar("hole-for-pigeon-$it", 0..1) }
        val separate =
            listOf(
                holes[0] neq holes[1],
                holes[0] neq holes[2],
                holes[1] neq holes[2],
            )
        val model = buildModel()
        // Sessions need a solver with incremental assertions: pick one among the platform's defaults.
        AutoBackend.load().use { auto ->
            val backend = auto.registry.select(model, requiredCapabilities = setOf(SolverCapability.INCREMENTAL_ASSERTIONS))
            backend.createSolver().use { solver ->
                (solver as IncrementalConstraintSolver).openSession(model).use { session ->
                    val conflict = session.check(assumptions = separate)
                    println(conflict.status)
                    val core = requireNotNull(conflict.unsatCore)
                    println("Conflicting pairwise requirements: ${core.size}")
                    val rechecked = session.check(core)
                    val subsets =
                        separate.indices.map { omitted ->
                            session.check(separate.filterIndexed { index, _ -> index != omitted })
                        }
                    // Assumptions apply to one check; the root model is still satisfiable.
                    val recovered = session.check()
                    println(recovered.status)
                    Triple(conflict to separate, rechecked, recovered to subsets)
                }
            }
        }
    }
// --8<-- [end:session]
