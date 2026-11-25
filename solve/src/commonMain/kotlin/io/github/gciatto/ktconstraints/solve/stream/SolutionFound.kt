package io.github.gciatto.ktconstraints.solve.stream
import io.github.gciatto.ktconstraints.core.Rational
import io.github.gciatto.ktconstraints.solve.assignment.Assignment
import org.gciatto.kt.math.BigInteger

/** A projected satisfaction solution or an improving optimization incumbent. */
data class SolutionFound(
    /** Feasible values for the requested output projection. */
    val assignment: Assignment,
    /** Exact objective value for an integer optimization incumbent, otherwise null. */
    val objectiveValue: BigInteger? = null,
    /** Exact objective value for a real optimization incumbent, otherwise null. */
    val realObjectiveValue: Rational? = null,
) : SolutionEvent {
    init {
        require(objectiveValue == null || realObjectiveValue == null) { "An objective is either integer or real" }
    }
}
