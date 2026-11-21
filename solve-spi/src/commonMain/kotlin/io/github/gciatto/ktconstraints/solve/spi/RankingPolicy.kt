package io.github.gciatto.ktconstraints.solve.spi

import kotlin.jvm.JvmField

/** Ordered weighted heuristics; a backend's rank is the weighted sum of their scores, ties keep registration order. */
class RankingPolicy(
    heuristics: List<WeightedHeuristic>,
) {
    /** Heuristics in evaluation order. */
    val heuristics: List<WeightedHeuristic> = buildList { addAll(heuristics) }

    /** This policy plus [heuristic] at [weight]. */
    fun with(
        heuristic: RankingHeuristic,
        weight: Double = 1.0,
    ): RankingPolicy = RankingPolicy(heuristics + WeightedHeuristic(heuristic, weight))

    /** This policy without heuristics named [name]. */
    fun without(name: String): RankingPolicy = RankingPolicy(heuristics.filterNot { it.heuristic.name == name })

    /** Built-in policies. */
    companion object {
        /** Capability fit, feature affinity and route overhead, equally weighted. Benchmark evidence is not included. */
        @JvmField
        val DEFAULT: RankingPolicy =
            RankingPolicy(
                listOf(Heuristics.capabilityFit, Heuristics.featureAffinity, Heuristics.routeOverhead).map { WeightedHeuristic(it) },
            )

        /** Registration order only. */
        @JvmField
        val REGISTRATION_ORDER: RankingPolicy = RankingPolicy(emptyList())
    }
}
