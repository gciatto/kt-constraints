package io.github.gciatto.ktconstraints.solve.spi.ranking
import io.github.gciatto.ktconstraints.solve.SolverDescriptor
import io.github.gciatto.ktconstraints.solve.spi.Platform
import io.github.gciatto.ktconstraints.solve.spi.feature.ModelFeatures

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
