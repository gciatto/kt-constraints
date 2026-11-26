package io.github.gciatto.ktconstraints.solve.spi.registry

import io.github.gciatto.ktconstraints.solve.SolverCapability
import io.github.gciatto.ktconstraints.solve.spi.exception.NoCompatibleBackendException
import io.github.gciatto.ktconstraints.solve.spi.feature.ModelFeatures

private fun hint(capability: SolverCapability): String =
    when (capability) {
        SolverCapability.NONLINEAR_REAL_OBJECTIVES -> " (the real objective multiplies variables, as a quadratic objective does)"
        else -> ""
    }

/** Ranked eligible backends for a model, with the reasons every other registered backend was rejected. */
class Selection(
    /** Features the ranking used. */
    val features: ModelFeatures,
    candidates: List<Candidate>,
    rejections: Map<String, List<String>>,
    /** Capabilities the model needs that no registered backend declares, so that only a new route could take it. */
    val unmetCapabilities: Set<SolverCapability> = emptySet(),
) {
    /**
     * What makes the model unsolvable here, for reports: the model's class and every capability no registered backend
     * declares, such as a non-linear real objective. Empty when some backend is eligible.
     */
    val diagnosis: List<String>
        get() =
            if (candidates.isNotEmpty()) {
                emptyList()
            } else {
                listOf("model: ${features.problemClass}") +
                    unmetCapabilities.map { "model needs $it, which no registered backend offers${hint(it)}" }
            }

    /** Eligible backends, best first. */
    val candidates: List<Candidate> = buildList { addAll(candidates) }

    /** Rejection reasons by `engine/route` ID. */
    val rejections: Map<String, List<String>> = buildMap { putAll(rejections) }

    /** The best candidate; fails with [NoCompatibleBackendException] when none is eligible. */
    val best: Candidate get() = candidates.firstOrNull() ?: throw NoCompatibleBackendException(rejections, diagnosis)
}
