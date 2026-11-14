package io.github.gciatto.ktconstraints.solve.minizinc

import io.github.gciatto.ktconstraints.core.Variable
import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.solve.spi.CompiledModel

/** Immutable MiniZinc source and original model; no process or temporary files are owned here. */
class MiniZincModel internal constructor(
    /** Original model, retained for independent full-assignment validation. */
    val model: ConstraintModel,
    /** Deterministic executable MiniZinc source. */
    val source: String,
) : CompiledModel {
    override val sourceVariables: Map<String, Variable> =
        buildMap {
            model.variables.forEachIndexed { index, variable -> put("v$index", variable) }
        }

    override fun close() = Unit
}
