package io.github.gciatto.ktconstraints.solve.auto.examples.problems

// --8<-- [start:imports]
import io.github.gciatto.ktconstraints.core.bool.BoolExpr
import io.github.gciatto.ktconstraints.core.integer.IntVar
import io.github.gciatto.ktconstraints.dsl.allDifferent
import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.model.ConstraintScope
import io.github.gciatto.ktconstraints.solve.auto.AutoBackend
import io.github.gciatto.ktconstraints.solve.incremental.CheckStatus
import io.github.gciatto.ktconstraints.solve.result.SolveResult
import io.github.gciatto.ktconstraints.solve.spi.withIncrementalSolver
import io.github.gciatto.ktconstraints.solve.spi.withSolver
// --8<-- [end:imports]

// --8<-- [start:model]

/** Places three pigeons into two holes, none shared; returns the hole of each pigeon. */
internal fun ConstraintScope.pigeonhole(): List<IntVar> {
    val holes = List(3) { intVar("hole-for-pigeon-$it", 0..1) }
    add(allDifferent(*holes.toTypedArray()))
    return holes
}
// --8<-- [end:model]

// --8<-- [start:solve]

/** Solves the pigeonhole model, which has no solution. */
internal suspend fun solvePigeonhole(): SolveResult {
    val model = ConstraintModel.of { pigeonhole() }
    val result = AutoBackend.load().use { auto -> auto.withSolver { it.solve(model) } }
    println(result.status)
    return result
}
// --8<-- [end:solve]

// --8<-- [start:session]

/** What the checks of [rules], the pairwise separation assumptions, revealed. */
internal data class PigeonholeConflict(
    val rules: List<BoolExpr>,
    val core: List<BoolExpr>,
    val coreStatus: CheckStatus,
    val withoutOneRule: List<CheckStatus>,
    val withoutAssumptions: CheckStatus,
)

/** Explains the pigeonhole conflict through an unsat core of temporary assumptions. */
internal suspend fun explainPigeonhole(): PigeonholeConflict =
    ConstraintScope.empty {
        val holes = List(3) { intVar("hole-for-pigeon-$it", 0..1) }
        val rules = listOf(holes[0] neq holes[1], holes[0] neq holes[2], holes[1] neq holes[2])
        val model = buildModel()
        AutoBackend.load().use { auto ->
            // The session runs on the best default route with incremental assertions.
            auto.withIncrementalSolver { solver ->
                solver.openSession(model).use { session ->
                    val conflict = session.check(assumptions = rules)
                    println(conflict.status)
                    check(conflict.status == CheckStatus.UNSATISFIABLE) { conflict.toString() }
                    val core = checkNotNull(conflict.unsatCore)
                    println("Conflicting pairwise requirements: ${core.size}")
                    PigeonholeConflict(
                        rules = rules,
                        core = core,
                        coreStatus = session.check(core).status,
                        withoutOneRule = rules.map { omitted -> session.check(rules - omitted).status },
                        // Assumptions apply to one check; the root model is still satisfiable.
                        withoutAssumptions = session.check().status.also { println(it) },
                    )
                }
            }
        }
    }
// --8<-- [end:session]
