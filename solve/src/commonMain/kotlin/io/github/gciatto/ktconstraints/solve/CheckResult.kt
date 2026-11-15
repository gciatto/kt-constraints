package io.github.gciatto.ktconstraints.solve

import io.github.gciatto.ktconstraints.core.BoolExpr

/** Result of one [IncrementalSession.check]. */
class CheckResult(
    /** Satisfiability outcome justified by the engine's evidence. */
    val status: CheckStatus,
    /** Model of the session's projected variables, present exactly when satisfiable. */
    val assignment: Assignment?,
    /** Engine and execution route that produced this result. */
    val descriptor: SolverDescriptor,
    /** Why the check stopped. */
    val terminationReason: TerminationReason = TerminationReason.COMPLETED,
    unsatCore: List<BoolExpr>? = null,
    /** Runtime messages, such as the engine's reason for an unknown outcome. */
    val metadata: SolveMetadata = SolveMetadata(),
) {
    /** Subset of the check's assumptions sufficient for unsatisfiability, not necessarily minimal. */
    val unsatCore: List<BoolExpr>? = unsatCore?.let { buildList { addAll(it) } }

    init {
        require((status == CheckStatus.SATISFIABLE) == (assignment != null)) { "Only satisfiable results carry an assignment" }
        require(status == CheckStatus.UNKNOWN || terminationReason == TerminationReason.COMPLETED) {
            "A proof status requires completed execution"
        }
        require(unsatCore == null || status == CheckStatus.UNSATISFIABLE) { "Only unsatisfiable results carry a core" }
    }

    override fun toString(): String = "CheckResult($status, $terminationReason, ${descriptor.id})"
}
