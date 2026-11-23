package io.github.gciatto.ktconstraints.solve.kotlin.compiler

import io.github.gciatto.ktconstraints.core.integer.IntVar
import io.github.gciatto.ktconstraints.model.ConstraintScope
import io.github.gciatto.ktconstraints.solve.kotlin.KotlinConfiguration
import io.github.gciatto.ktconstraints.solve.kotlin.compiler.circuit.Circuit
import io.github.gciatto.ktconstraints.solve.kotlin.sat.SatSolver
import io.github.gciatto.ktconstraints.solve.kotlin.search.SearchBudget

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
