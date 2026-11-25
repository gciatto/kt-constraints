package io.github.gciatto.ktconstraints.solve.kotlin.lp

import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.model.isReal
import io.github.gciatto.ktconstraints.solve.linear.LinearProgram
import io.github.gciatto.ktconstraints.solve.spi.support.SupportReport

/** Which models the pure-Kotlin route solves as linear or mixed-integer programs, with exact branch and bound. */
internal object LinearPrograms {
    /** Whether [model] goes to branch and bound: a real objective, which the other techniques do not optimize. */
    fun handles(model: ConstraintModel): Boolean = model.objective?.isReal == true

    /** Why branch and bound cannot solve [model], which it [handles]: anything outside the linear fragment. */
    fun report(model: ConstraintModel): SupportReport = LinearProgram.report(model)
}
