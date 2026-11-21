package io.github.gciatto.ktconstraints.solve.kotlin.compiler

import io.github.gciatto.ktconstraints.core.IntVar
import io.github.gciatto.ktconstraints.model.ConstraintScope
import io.github.gciatto.ktconstraints.solve.kotlin.KotlinConfiguration
import io.github.gciatto.ktconstraints.solve.kotlin.SearchBudget
import io.github.gciatto.ktconstraints.solve.kotlin.sat.SatSolver

/** Isolated circuit and declaration table for exact compiler tests. */
internal class TranslationFixture {
    val budget = SearchBudget(null, null) { false }
    val solver = SatSolver()
    val circuit = Circuit(solver, KotlinConfiguration(), budget)
    val translation = KotlinTranslation(circuit)

    suspend fun declare(scope: ConstraintScope) {
        val model = scope.build()
        val domains = model.variables.filterIsInstance<IntVar>().associateWith { it.domain }
        translation.declare(model, domains, budget)
    }
}
