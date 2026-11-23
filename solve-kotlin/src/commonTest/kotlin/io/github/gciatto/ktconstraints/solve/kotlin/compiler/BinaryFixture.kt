package io.github.gciatto.ktconstraints.solve.kotlin.compiler

import io.github.gciatto.ktconstraints.solve.kotlin.KotlinConfiguration
import io.github.gciatto.ktconstraints.solve.kotlin.compiler.circuit.BinaryArithmetic
import io.github.gciatto.ktconstraints.solve.kotlin.compiler.circuit.Circuit
import io.github.gciatto.ktconstraints.solve.kotlin.sat.SatSolver
import io.github.gciatto.ktconstraints.solve.kotlin.search.SearchBudget

/** Every test owns its variable universe and SAT clauses. */
internal class BinaryFixture {
    val solver = SatSolver()
    val circuit = Circuit(solver, KotlinConfiguration(), SearchBudget(null, null) { false })
    val arithmetic = BinaryArithmetic(circuit)
}
