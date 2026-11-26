package io.github.gciatto.ktconstraints.test.solve

import io.github.gciatto.ktconstraints.core.Rational
import io.github.gciatto.ktconstraints.model.ConstraintScope
import io.github.gciatto.ktconstraints.solve.result.SolveStatus

/** One linear or mixed-integer linear program with its exact expected outcome. */
data class LinearCase(
    /** Stable test name. */
    val name: String,
    /** Expected status. */
    val status: SolveStatus,
    /** Expected exact optimum, for [SolveStatus.OPTIMAL]. */
    val optimum: Rational? = null,
    /** Declares the program in a fresh scope. */
    val build: ConstraintScope.() -> Unit,
) {
    override fun toString(): String = name
}
