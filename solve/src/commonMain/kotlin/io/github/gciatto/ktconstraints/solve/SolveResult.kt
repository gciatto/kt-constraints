package io.github.gciatto.ktconstraints.solve

import org.gciatto.kt.math.BigInteger

/** Result of a one-shot CP/SMT solve. */
data class SolveResult(
    /** Search outcome justified by the backend's evidence. */
    val status: SolveStatus,
    /** Feasible incumbent, if one was found. */
    val assignment: Assignment?,
    /** Engine and execution route that produced this result. */
    val descriptor: SolverDescriptor,
    /** Why execution stopped. */
    val terminationReason: TerminationReason = TerminationReason.COMPLETED,
    /** Exact incumbent objective, when optimizing. */
    val objectiveValue: BigInteger? = null,
    /** Exact proven objective bound, when supplied by the backend. */
    val bestBound: BigInteger? = null,
) {
    init {
        val feasible = status == SolveStatus.FEASIBLE || status == SolveStatus.OPTIMAL
        require(feasible == (assignment != null)) { "Only feasible/optimal results carry an assignment" }
        require(objectiveValue == null || assignment != null) { "An objective value requires an assignment" }
        require(status != SolveStatus.OPTIMAL || objectiveValue != null) { "Optimality requires an objective value" }
        require(status != SolveStatus.OPTIMAL || bestBound == null || bestBound == objectiveValue) {
            "An optimal objective and its provided bound must agree"
        }
        require(status == SolveStatus.UNKNOWN || status == SolveStatus.FEASIBLE || terminationReason == TerminationReason.COMPLETED) {
            "A proof status requires completed execution"
        }
    }
}
