package io.github.gciatto.ktconstraints.solve.spi.incremental

import io.github.gciatto.ktconstraints.core.Variable
import io.github.gciatto.ktconstraints.core.bool.BoolExpr
import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.model.ConstraintScope
import io.github.gciatto.ktconstraints.model.Objective

/** Batch models standing for an incremental session's state, for routes that optimize a session by solving them. */
object SessionModels {
    /**
     * [base] plus the session's [declared] variables, its active [constraints] beyond the base ones, and [objective],
     * projected on the base outputs followed by the declared variables, as session checks report them.
     */
    fun of(
        base: ConstraintModel,
        declared: List<Variable>,
        constraints: List<BoolExpr>,
        objective: Objective,
    ): ConstraintModel =
        ConstraintScope
            .of(base)
            .apply {
                declared.forEach { declare(it) }
                constraints.forEach { add(it) }
                when (objective) {
                    is Objective.Minimize -> minimize(objective.expression)
                    is Objective.Maximize -> maximize(objective.expression)
                    is Objective.MinimizeReal -> minimize(objective.expression)
                    is Objective.MaximizeReal -> maximize(objective.expression)
                }
                project(*(base.outputVariables + declared).toTypedArray())
            }.buildModel()
}
