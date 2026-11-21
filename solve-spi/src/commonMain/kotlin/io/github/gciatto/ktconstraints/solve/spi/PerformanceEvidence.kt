package io.github.gciatto.ktconstraints.solve.spi

import io.github.gciatto.ktconstraints.solve.ExperimentalKtConstraintsApi

/**
 * Measured performance of routes on problem classes, the hook for benchmark-driven ranking. Answers the expected
 * milliseconds for [route] (an `engine/route` ID) on a model of [problemClass] with [features], or null when unknown.
 */
@ExperimentalKtConstraintsApi
fun interface PerformanceEvidence {
    /** Expected solve time in milliseconds, or null without evidence. */
    fun expectedMillis(
        problemClass: ProblemClass,
        route: String,
        features: ModelFeatures,
    ): Double?

    /** No evidence. */
    companion object {
        /** Knows nothing, so it never changes a ranking. */
        val NONE: PerformanceEvidence = PerformanceEvidence { _, _, _ -> null }
    }
}
