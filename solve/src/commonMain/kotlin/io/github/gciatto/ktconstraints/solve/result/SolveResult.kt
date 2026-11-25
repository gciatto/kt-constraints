package io.github.gciatto.ktconstraints.solve.result
import io.github.gciatto.ktconstraints.core.Rational
import io.github.gciatto.ktconstraints.solve.SolverDescriptor
import io.github.gciatto.ktconstraints.solve.assignment.Assignment
import org.gciatto.kt.math.BigInteger

/**
 * Result of a one-shot solve. Integer objectives report [objectiveValue] and [bestBound]; real objectives report
 * [realObjectiveValue] and [realBestBound] instead, as exact rationals.
 */
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
    /** Immutable runtime messages and backend statistics, when provided. */
    val metadata: SolveMetadata = SolveMetadata(),
    /** Exact incumbent value of a real objective. */
    val realObjectiveValue: Rational? = null,
    /**
     * Exact proven bound of a real objective, when supplied by the backend. With no [realObjectiveValue] equal to it,
     * the bound may be a supremum (or infimum) that no solution attains, as when maximizing `x` subject to `x < 1`.
     */
    val realBestBound: Rational? = null,
) {
    init {
        val feasible = status == SolveStatus.FEASIBLE || status == SolveStatus.OPTIMAL
        require(feasible == (assignment != null)) { "Only feasible/optimal results carry an assignment" }
        require(objectiveValue == null || assignment != null) { "An objective value requires an assignment" }
        require(realObjectiveValue == null || assignment != null) { "An objective value requires an assignment" }
        require((objectiveValue == null && bestBound == null) || (realObjectiveValue == null && realBestBound == null)) {
            "An objective is either integer or real"
        }
        require(status != SolveStatus.OPTIMAL || objectiveValue != null || realObjectiveValue != null) {
            "Optimality requires an objective value"
        }
        require(status != SolveStatus.OPTIMAL || bestBound == null || bestBound == objectiveValue) {
            "An optimal objective and its provided bound must agree"
        }
        require(status != SolveStatus.OPTIMAL || realBestBound == null || realBestBound == realObjectiveValue) {
            "An optimal objective and its provided bound must agree"
        }
        require(status == SolveStatus.UNKNOWN || status == SolveStatus.FEASIBLE || terminationReason == TerminationReason.COMPLETED) {
            "A proof status requires completed execution"
        }
    }
}
