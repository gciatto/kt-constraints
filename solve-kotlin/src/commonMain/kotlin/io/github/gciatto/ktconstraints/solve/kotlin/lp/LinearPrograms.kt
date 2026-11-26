package io.github.gciatto.ktconstraints.solve.kotlin.lp

import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.model.isReal
import io.github.gciatto.ktconstraints.solve.linear.LinearProgram
import io.github.gciatto.ktconstraints.solve.spi.feature.ModelFeatures
import io.github.gciatto.ktconstraints.solve.spi.support.SupportReport

/**
 * Which models the pure-Kotlin route solves with exact branch and cut over the simplex, rather than with its SAT
 * engine: every real objective, which only branch and cut optimizes, and every integer objective over a linear model
 * (a linear, 0-1 or mixed-integer program). On the benchmark families, branch and cut beat the SAT encoding on all of
 * those, by up to four orders of magnitude when integer domains are wide. Satisfaction, enumeration, logic, globals
 * and products stay with the SAT engine.
 */
internal object LinearPrograms {
    /** Whether [model] goes to branch and cut. */
    fun handles(model: ConstraintModel): Boolean {
        val goal = model.objective ?: return false
        return goal.isReal || ModelFeatures.of(model).linear && LinearProgram.report(model).isSupported
    }

    /** Why branch and cut cannot solve [model], which it [handles]: anything outside the linear fragment. */
    fun report(model: ConstraintModel): SupportReport = LinearProgram.report(model)
}
