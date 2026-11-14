package io.github.gciatto.ktconstraints.solve.spi

import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.solve.SolveOptions
import io.github.gciatto.ktconstraints.solve.SolverCapability
import kotlin.jvm.JvmStatic

/** Immutable explicit registry; deterministic eligibility selection never instantiates a solver. */
class SolverRegistry private constructor(backends: Collection<SolverBackend>) {
    private val backends: List<SolverBackend> = buildList { addAll(backends) }

    init {
        require(this.backends.map { it.descriptor.id }.toSet().size == this.backends.size) { "Duplicate backend route ID" }
    }

    /** Returns a registered route, failing explicitly for unknown IDs. */
    fun require(id: String): SolverBackend =
        requireNotNull(backends.firstOrNull { it.descriptor.id == id }) { "Backend not registered: $id" }

    /** Chooses the first eligible registered backend, retaining diagnostics when none qualifies. */
    fun select(
        model: ConstraintModel,
        options: SolveOptions = SolveOptions.DEFAULT,
        requiredCapabilities: Set<SolverCapability> = emptySet(),
    ): SolverBackend {
        val required = buildSet {
            add(SolverCapability.SATISFACTION)
            if (model.objective != null) add(SolverCapability.OPTIMIZATION)
            addAll(requiredCapabilities)
        }
        val rejections = linkedMapOf<String, List<String>>()
        for (backend in backends) {
            val reasons = rejectionReasons(backend, model, options, required)
            if (reasons.isEmpty()) return backend
            rejections[backend.descriptor.id] = reasons
        }
        throw NoCompatibleBackendException(rejections)
    }

    private fun rejectionReasons(
        backend: SolverBackend,
        model: ConstraintModel,
        options: SolveOptions,
        required: Set<SolverCapability>,
    ): List<String> {
        val missing = required - backend.descriptor.capabilities
        if (missing.isNotEmpty()) return missing.sortedBy { it.ordinal }.map { "Missing capability: $it" }
        val availability = backend.availability()
        if (!availability.available) return listOf(requireNotNull(availability.reason))
        return backend.support(model, options).unsupported
    }

    /** Portable factories; there is no classpath discovery or implicit registration. */
    companion object {
        /** Creates a registry in the given backend preference order. */
        @JvmStatic
        fun of(vararg backends: SolverBackend): SolverRegistry = SolverRegistry(backends.asList())

        /** Creates a registry with no backends. Test fakes are never registered automatically. */
        @JvmStatic
        fun empty(): SolverRegistry = of()
    }
}
