package io.github.gciatto.ktconstraints.solve.spi

/** An eligible backend with its rank and the contributions that produced it. */
class Candidate(
    /** The backend. */
    val backend: SolverBackend,
    contributions: List<Contribution>,
) {
    /** Per-heuristic contributions, in policy order; heuristics without an opinion are absent. */
    val contributions: List<Contribution> = buildList { addAll(contributions) }

    /** Weighted sum of the scores. */
    val total: Double = contributions.sumOf { it.weight * it.score.value }

    /** One line per contribution, for diagnostics. */
    val explanation: String
        get() = contributions.joinToString("; ") { "${it.heuristic}: ${it.score.reason} (${it.score.value} x ${it.weight})" }

    override fun toString(): String = "${backend.descriptor.id} [$total] $explanation"
}
