package io.github.gciatto.ktconstraints.solve.kotlin.lp

import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.model.isReal
import io.github.gciatto.ktconstraints.solve.linear.LinearProgram
import io.github.gciatto.ktconstraints.solve.spi.support.SupportReport

/** Which models the pure-Kotlin route solves as linear programs with its exact simplex. */
internal object LinearPrograms {
    /** Mixed-integer programs with real objectives await branch and bound. */
    const val MIXED_INTEGER = "Real objective over integer variables: mixed-integer programs are not optimized here yet"

    /** Whether [model] goes to the simplex: a real objective, which the other techniques do not optimize. */
    fun handles(model: ConstraintModel): Boolean = model.objective?.isReal == true

    /** Why the simplex cannot solve [model], which it [handles]. */
    fun report(model: ConstraintModel): SupportReport {
        val linear = LinearProgram.report(model)
        if (!linear.isSupported) return linear
        return if (LinearProgram.of(model).hasIntegers) SupportReport(listOf(MIXED_INTEGER)) else SupportReport()
    }
}
