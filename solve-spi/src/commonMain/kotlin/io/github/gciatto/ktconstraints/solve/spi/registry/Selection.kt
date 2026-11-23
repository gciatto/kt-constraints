package io.github.gciatto.ktconstraints.solve.spi.registry

import io.github.gciatto.ktconstraints.solve.spi.exception.NoCompatibleBackendException
import io.github.gciatto.ktconstraints.solve.spi.feature.ModelFeatures

/** Ranked eligible backends for a model, with the reasons every other registered backend was rejected. */
class Selection(
    /** Features the ranking used. */
    val features: ModelFeatures,
    candidates: List<Candidate>,
    rejections: Map<String, List<String>>,
) {
    /** Eligible backends, best first. */
    val candidates: List<Candidate> = buildList { addAll(candidates) }

    /** Rejection reasons by `engine/route` ID. */
    val rejections: Map<String, List<String>> = buildMap { putAll(rejections) }

    /** The best candidate; fails with [NoCompatibleBackendException] when none is eligible. */
    val best: Candidate get() = candidates.firstOrNull() ?: throw NoCompatibleBackendException(rejections)
}
