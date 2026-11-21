package io.github.gciatto.ktconstraints.solve.spi

import io.github.gciatto.ktconstraints.solve.SolverDescriptor

/** Scores eligible backends for a model; implement it to extend `auto` selection. */
interface RankingHeuristic {
    /** Stable name shown in explanations. */
    val name: String

    /** Opinion of [descriptor] for a model with [features] on [platform]; null for no opinion. */
    fun score(
        features: ModelFeatures,
        descriptor: SolverDescriptor,
        platform: Platform,
    ): Score?
}
